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
            case "database.metric.query" -> "查询业务指标数据";
            case "report.generate" -> "生成系统健康报表";
            case "notification.send" -> "发送通知消息";
            default -> "未知工具";
        };
    }

    private Map<String, Object> buildParamSchema(String toolCode) {
        // 简化版，返回空schema
        // 实际应该根据每个工具的入参定义完整的JSON Schema
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", new HashMap<>());
        schema.put("required", new ArrayList<>());
        return schema;
    }
}
