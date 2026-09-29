package com.hermes.agent.tool;

import com.hermes.agent.common.enums.SafetyLevel;
import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 工具定义
 */
@Data
public class ToolDefinition {

    /**
     * 工具编码（唯一）
     */
    private String toolCode;

    /**
     * 展示名
     */
    private String displayName;

    /**
     * 安全等级
     */
    private SafetyLevel safetyLevel;

    /**
     * 参数Schema（JSON Schema）
     */
    private Map<String, Object> paramSchema;

    /**
     * 需要注入的数据范围字段
     */
    private List<String> scopeFields;

    /**
     * 超时时间(ms)
     */
    private Integer timeoutMs = 10000;

    /**
     * 重试策略
     */
    private RetryPolicy retryPolicy;

    /**
     * 是否启用
     */
    private Boolean enabled = true;

    /**
     * 描述
     */
    private String description;

    @Data
    public static class RetryPolicy {
        private Integer maxRetries = 0;
        private Long backoffMs = 1000L;
    }
}
