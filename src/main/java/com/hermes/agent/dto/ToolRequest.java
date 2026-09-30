package com.hermes.agent.dto;

import lombok.Data;
import java.util.Map;

/**
 * 工具调用请求信封（对齐原方案 §11.4）
 */
@Data
public class ToolRequest {
    /**
     * 调用链ID
     */
    private String traceId;

    /**
     * 会话ID（审批是会话级的，allow_session 依赖它）
     */
    private String sessionId;

    /**
     * 发起调用的身份包
     */
    private String agentCode;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 租户ID
     */
    private String tenantId;

    /**
     * 目标环境
     */
    private String environment;

    /**
     * 超时时间(ms)
     */
    private Integer timeoutMs = 10000;

    /**
     * 是否干跑（WRITE级工具强制先校验）
     */
    private Boolean dryRun = false;

    /**
     * 工具参数
     */
    private Map<String, Object> arguments;
}
