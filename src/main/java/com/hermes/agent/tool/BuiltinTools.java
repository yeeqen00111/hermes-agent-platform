package com.hermes.agent.tool;

import com.hermes.agent.common.enums.SafetyLevel;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 内置工具定义（对齐原方案 §11.3）
 */
@Component
@RequiredArgsConstructor
public class BuiltinTools {

    private final ToolRegistry toolRegistry;

    @PostConstruct
    public void registerBuiltins() {
        toolRegistry.registerAll(getBuiltinTools());
    }

    /**
     * 获取所有内置工具定义
     */
    public List<ToolDefinition> getBuiltinTools() {
        List<ToolDefinition> tools = new ArrayList<>();

        // 日志相关
        tools.add(createTool("log.search", "搜索日志", SafetyLevel.READ,
                Arrays.asList("environment", "projectCode", "serviceName")));
        tools.add(createTool("log.context", "获取日志上下文", SafetyLevel.READ,
                Collections.singletonList("environment")));
        tools.add(createTool("log.aggregate", "聚合日志统计", SafetyLevel.READ,
                Arrays.asList("environment", "projectCode", "serviceName")));

        // 告警相关
        tools.add(createTool("alert.query", "查询告警", SafetyLevel.READ,
                Arrays.asList("environment", "serviceName")));
        tools.add(createTool("alert.acknowledge", "确认告警", SafetyLevel.WRITE,
                Arrays.asList("environment")));
        tools.add(createTool("alert.resolve", "解决告警", SafetyLevel.WRITE,
                Arrays.asList("environment")));
        tools.add(createTool("alert.suppress", "静默告警", SafetyLevel.WRITE,
                Arrays.asList("environment")));

        // Nacos相关
        tools.add(createTool("nacos.change.query", "查询Nacos变更", SafetyLevel.READ,
                Collections.singletonList("environment")));
        tools.add(createTool("nacos.config.query", "查询Nacos配置", SafetyLevel.READ,
                Collections.singletonList("environment")));
        tools.add(createTool("nacos.instance.query", "查询Nacos实例", SafetyLevel.READ,
                Collections.singletonList("environment")));

        // 知识库
        tools.add(createTool("knowledge.search", "搜索知识库", SafetyLevel.READ,
                Collections.emptyList()));

        // 技能（§6.2 渐进披露：索引进系统提示，正文按名加载）
        tools.add(createTool("skill.load", "按技能编码加载技能全文", SafetyLevel.READ,
                Collections.singletonList("skillCode")));

        // 指标查询（暂缺Java接口）
        tools.add(createTool("database.metric.query", "查询业务指标", SafetyLevel.READ,
                Collections.singletonList("environment")));

        // 报表和通知
        tools.add(createTool("report.generate", "生成健康报表", SafetyLevel.CONTROLLED,
                Collections.singletonList("environment")));
        tools.add(createTool("notification.send", "发送通知", SafetyLevel.CONTROLLED,
                Collections.emptyList()));

        return tools;
    }

    private ToolDefinition createTool(String code, String displayName, SafetyLevel level,
                                      List<String> scopeFields) {
        ToolDefinition def = new ToolDefinition();
        def.setToolCode(code);
        def.setDisplayName(displayName);
        def.setSafetyLevel(level);
        def.setScopeFields(scopeFields);
        def.setDescription(buildDescription(code));
        def.setParamSchema(buildParamSchema(code));
        return def;
    }

    private String buildDescription(String toolCode) {
        // 简化版，实际应该更详细
        return switch (toolCode) {
            case "log.search" -> "搜索应用日志，支持按服务、环境、级别、关键词过滤";
            case "log.context" -> "获取指定日志事件的上下文（前后N条）";
            case "log.aggregate" -> "对日志进行聚合统计";
            case "alert.query" -> "查询告警列表，支持按等级、状态、服务过滤";
            case "alert.acknowledge" -> "确认告警（需人工审批）";
            case "alert.resolve" -> "标记告警已解决（需人工审批）";
            case "alert.suppress" -> "静默告警（需人工审批）";
            case "nacos.change.query" -> "查询Nacos配置变更记录";
            case "nacos.config.query" -> "查询Nacos当前配置快照";
            case "nacos.instance.query" -> "查询Nacos服务实例信息";
            case "knowledge.search" -> "搜索运维知识库";
            case "skill.load" -> "按技能编码加载技能的SKILL.md全文（可用技能见系统提示索引）";
            case "database.metric.query" -> "查询业务指标数据";
            case "report.generate" -> "生成系统健康报表";
            case "notification.send" -> "发送通知（需人工审批）：channelType=FEISHU/EMAIL，recipient 接收人（邮箱/飞书），title 标题，content 内容；走平台通知网关（◆ 复用控制塔告警通道）";
            default -> "未知工具";
        };
    }

    /**
     * 参数Schema（对齐 interface-contract §4.3 逐工具入参；由 Guardrail.validateParamSchema 强制执行必填与数值类型）
     */
    private Map<String, Object> buildParamSchema(String toolCode) {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        switch (toolCode) {
            case "log.search" -> {
                properties.put("service", strProp("服务名"));
                properties.put("environment", strProp("环境（prod/staging/test）"));
                properties.put("level", strProp("日志级别（ERROR/WARN/INFO/DEBUG）"));
                properties.put("keyword", strProp("关键词"));
                properties.put("traceId", strProp("链路ID"));
                properties.put("from", strProp("起始时间（ISO-8601）"));
                properties.put("to", strProp("结束时间（ISO-8601）"));
                properties.put("limit", intProp("返回条数"));
                required.addAll(List.of("service", "from", "to"));
            }
            case "log.context" -> {
                properties.put("eventId", strProp("日志事件ID"));
                properties.put("before", intProp("向前取 N 条（默认5）"));
                properties.put("after", intProp("向后取 N 条（默认5）"));
                required.add("eventId");
            }
            case "log.aggregate" -> {
                properties.put("service", strProp("服务名"));
                properties.put("environment", strProp("环境"));
                properties.put("groupBy", strProp("聚合维度（level/service/exceptionType）"));
                properties.put("from", strProp("起始时间（ISO-8601）"));
                properties.put("to", strProp("结束时间（ISO-8601）"));
                required.addAll(List.of("service", "from", "to"));
            }
            case "alert.query" -> {
                properties.put("severity", strProp("等级（P0-P3）"));
                properties.put("status", strProp("状态（NEW/CONFIRMED/RESOLVED）"));
                properties.put("service", strProp("服务名"));
                properties.put("environment", strProp("环境"));
                properties.put("from", strProp("起始时间（ISO-8601）"));
                properties.put("to", strProp("结束时间（ISO-8601）"));
                properties.put("limit", intProp("返回条数"));
            }
            case "alert.acknowledge" -> {
                properties.put("alertId", strProp("告警ID"));
                properties.put("comment", strProp("确认备注"));
                required.add("alertId");
            }
            case "alert.resolve" -> {
                properties.put("alertId", strProp("告警ID"));
                properties.put("comment", strProp("解决备注"));
                required.add("alertId");
            }
            case "alert.suppress" -> {
                properties.put("alertId", strProp("告警ID"));
                properties.put("comment", strProp("静默原因"));
                required.add("alertId");
            }
            case "nacos.change.query" -> {
                properties.put("namespace", strProp("命名空间"));
                properties.put("group", strProp("配置分组"));
                properties.put("dataId", strProp("配置ID"));
                properties.put("from", strProp("起始时间（ISO-8601）"));
                properties.put("to", strProp("结束时间（ISO-8601）"));
                properties.put("limit", intProp("返回条数"));
                required.addAll(List.of("from", "to"));
            }
            case "nacos.config.query" -> {
                properties.put("namespace", strProp("命名空间"));
                properties.put("group", strProp("配置分组"));
                properties.put("dataId", strProp("配置ID"));
                required.addAll(List.of("namespace", "group", "dataId"));
            }
            case "nacos.instance.query" -> {
                properties.put("service", strProp("服务名"));
                properties.put("namespace", strProp("命名空间"));
                properties.put("cluster", strProp("集群"));
                properties.put("environment", strProp("环境"));
            }
            case "knowledge.search" -> {
                properties.put("query", strProp("检索问题"));
                properties.put("knowledgeBaseIds", strProp("知识库ID列表"));
                properties.put("projectId", strProp("项目ID（权限过滤）"));
                properties.put("topK", intProp("返回条数（默认5）"));
                required.add("query");
            }
            case "skill.load" -> {
                properties.put("skillCode", strProp("技能编码"));
                required.add("skillCode");
            }
            case "database.metric.query" -> {
                properties.put("metric", strProp("指标编码"));
                properties.put("dimensions", strProp("维度（JSON）"));
                properties.put("filters", strProp("过滤条件（JSON）"));
                properties.put("from", strProp("起始时间（ISO-8601）"));
                properties.put("to", strProp("结束时间（ISO-8601）"));
                required.addAll(List.of("metric", "from", "to"));
            }
            case "report.generate" -> {
                properties.put("scope", strProp("人工选定范围（environment/system/service，JSON）"));
                properties.put("from", strProp("起始时间（ISO-8601）"));
                properties.put("to", strProp("结束时间（ISO-8601）"));
                properties.put("format", strProp("输出格式（md/html/xlsx）"));
                required.addAll(List.of("from", "to"));
            }
            case "notification.send" -> {
                properties.put("channelType", strProp("通道类型（FEISHU/EMAIL）"));
                properties.put("recipient", strProp("接收人（邮箱/飞书）"));
                properties.put("title", strProp("标题"));
                properties.put("content", strProp("内容"));
                required.addAll(List.of("channelType", "recipient", "content"));
            }
            default -> {
            }
        }

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        return schema;
    }

    private Map<String, Object> strProp(String description) {
        return Map.of("type", "string", "description", description);
    }

    private Map<String, Object> intProp(String description) {
        return Map.of("type", "integer", "description", description);
    }
}
