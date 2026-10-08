package com.hermes.agent.tool;

import com.hermes.agent.dto.ToolRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 安全护栏（工具调用前的校验，对齐 interface-contract §4.3）。
 */
@Slf4j
@Component
public class Guardrail {

    /** 契约 §3.6 的数据范围维度；其余 scopeFields（如 skillCode）非数据维度，不参与校验 */
    private static final Set<String> SCOPE_DIMENSIONS = Set.of("environment", "projectCode", "serviceName", "system");

    /** scopeFields 名 → 工具入参名（历史命名差异：serviceName 对应入参 service） */
    private static final Map<String, String> SCOPE_ARG_ALIAS = Map.of("serviceName", "service");

    /**
     * 校验结果
     */
    public record ValidationResult(boolean valid, String errorCode, String errorMessage) {
        public static ValidationResult success() {
            return new ValidationResult(true, null, null);
        }

        public static ValidationResult fail(String errorCode, String errorMessage) {
            return new ValidationResult(false, errorCode, errorMessage);
        }
    }

    /**
     * 校验工具调用
     */
    public ValidationResult validate(ToolDefinition toolDef, ToolRequest request) {
        // 1. 参数Schema校验
        if (!validateParamSchema(toolDef, request)) {
            return ValidationResult.fail("INVALID_ARGUMENT", "参数不符合Schema要求");
        }

        // 2. 数据范围校验
        if (!validateDataScope(toolDef, request)) {
            return ValidationResult.fail("SCOPE_DENIED", "超出允许的数据范围");
        }

        // 3. 超时时间校验
        if (request.getTimeoutMs() != null && request.getTimeoutMs() > 60000) {
            return ValidationResult.fail("INVALID_ARGUMENT", "超时时间不能超过60秒");
        }

        return ValidationResult.success();
    }

    /**
     * 参数 Schema 校验（对齐 interface-contract §4.3 逐工具入参）：
     * ① 必填参数必须存在且非空白（如 log.search 的 service/from/to、alert.* 的 alertId）；
     * ② 声明为 integer/number 的字段必须可数值化。
     * 说明：JSON 对象/数组型字段（如 report.generate.scope）在 schema 中标注为 string，
     * 且运行时参数经 asText 归一为标量，故不对 string 类型做严格判定；未知参数从宽放行（兼容扩展/MCP）。
     */
    private boolean validateParamSchema(ToolDefinition toolDef, ToolRequest request) {
        Map<String, Object> schema = toolDef.getParamSchema();
        if (schema == null || schema.isEmpty()) {
            return true;
        }
        Map<String, Object> args = request.getArguments() == null ? Map.of() : request.getArguments();

        Object requiredObj = schema.get("required");
        if (requiredObj instanceof List<?> required) {
            for (Object item : required) {
                String field = String.valueOf(item);
                if (isBlank(args.get(field))) {
                    log.warn("工具 {} 缺少必填参数: {}", toolDef.getToolCode(), field);
                    return false;
                }
            }
        }

        Object propsObj = schema.get("properties");
        if (propsObj instanceof Map<?, ?> props) {
            for (Map.Entry<String, Object> entry : args.entrySet()) {
                Object propDef = props.get(entry.getKey());
                if (propDef instanceof Map<?, ?> pd && !isNumericTypeCompatible(pd.get("type"), entry.getValue())) {
                    log.warn("工具 {} 参数 {} 类型不符: {}", toolDef.getToolCode(), entry.getKey(), entry.getValue());
                    return false;
                }
            }
        }
        return true;
    }

    private boolean isBlank(Object value) {
        return value == null || (value instanceof String s && s.isBlank());
    }

    /** 仅对声明为 integer/number 的字段做数值可解析校验，其余类型从宽 */
    private boolean isNumericTypeCompatible(Object declaredType, Object value) {
        if (value == null) {
            return true;
        }
        String type = declaredType == null ? "" : String.valueOf(declaredType);
        if (!"integer".equals(type) && !"number".equals(type)) {
            return true;
        }
        if (value instanceof Number) {
            return true;
        }
        if (value instanceof String s) {
            String t = s.trim();
            if (t.isEmpty()) {
                return true; // 空串视为未提供，由必填项决定
            }
            try {
                if ("integer".equals(type)) {
                    Long.parseLong(t);
                } else {
                    Double.parseDouble(t);
                }
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return false; // Map/List/Boolean 等非数值
    }

    private boolean validateDataScope(ToolDefinition toolDef, ToolRequest request) {
        List<String> scopeFields = toolDef.getScopeFields();
        if (scopeFields == null || scopeFields.isEmpty()) {
            return true;
        }
        // 只有真正的数据维度参与校验；skillCode 等非数据维度（如 skill.load）跳过，避免误拦
        Map<String, List<String>> scope = request.getDataScope();
        if (scope == null || scope.isEmpty()) {
            // X-Data-Scope 未注入（Java 数据面尚未接线）：放行并告警，与 §3.7「未配置则开发放行并告警」同策略（ADR-013）
            log.warn("工具 {} 声明数据范围 {} 但本轮未注入 X-Data-Scope，放行（fail-open）",
                    toolDef.getToolCode(), scopeFields);
            return true;
        }
        Map<String, Object> args = request.getArguments() == null ? Map.of() : request.getArguments();
        for (String field : scopeFields) {
            if (!SCOPE_DIMENSIONS.contains(field)) {
                continue;
            }
            String requested = requestedValue(field, args, request);
            if (requested == null || requested.isBlank()) {
                continue; // 调用未限定该维度，交由下游按 X-Data-Scope 做行级过滤
            }
            List<String> allowed = scope.get(field);
            if (allowed == null || allowed.isEmpty()) {
                continue; // Java 未对该维度设限 → 不限制
            }
            boolean ok = allowed.stream().anyMatch(a -> "*".equals(a) || matches(field, a, requested));
            if (!ok) {
                log.warn("工具 {} 入参 {}={} 超出 X-Data-Scope 允许范围 {}",
                        toolDef.getToolCode(), field, requested, allowed);
                return false;
            }
        }
        return true;
    }

    /** 数据维度 → 工具入参名（历史命名差异：scopeFields 的 serviceName 对应入参 service） */
    private String requestedValue(String field, Map<String, Object> args, ToolRequest request) {
        // environment 以服务端注入的 X-Actor-Env 为权威，防止模型在入参里伪造环境绕过
        if ("environment".equals(field) && request.getEnvironment() != null && !request.getEnvironment().isBlank()) {
            return request.getEnvironment();
        }
        String argName = SCOPE_ARG_ALIAS.getOrDefault(field, field);
        Object value = args.get(argName);
        return value == null ? null : String.valueOf(value);
    }

    /** environment 大小写不敏感（prod/PROD），其余维度精确匹配 */
    private boolean matches(String field, String allowed, String requested) {
        return "environment".equals(field) ? allowed.equalsIgnoreCase(requested) : allowed.equals(requested);
    }
}
