package com.hermes.agent.dto;

import lombok.Data;
import java.util.List;
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
     * 调用渠道：chat / flow / review
     */
    private String channel;

    /**
     * 超时时间(ms)
     */
    private Integer timeoutMs = 10000;

    /**
     * 是否干跑（WRITE级工具强制先校验）
     */
    private Boolean dryRun = false;

    /**
     * 数据范围（服务端由 X-Data-Scope 注入，模型不可见不可改）：
     * 键=维度（environment/projectCode/serviceName/system），值=允许值，{@code "*"} 通配
     */
    private Map<String, List<String>> dataScope;

    /**
     * 工具参数
     */
    private Map<String, Object> arguments;
}
