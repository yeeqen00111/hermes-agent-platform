package com.hermes.agent.common.enums;

/**
 * SSE事件类型
 */
public enum SSEEventType {
    /**
     * 正文增量
     */
    MESSAGE_DELTA("message.delta"),

    /**
     * 思考增量
     */
    REASONING_DELTA("reasoning.delta"),

    /**
     * 工具调用开始
     */
    TOOL_START("tool.start"),

    /**
     * 工具调用结束
     */
    TOOL_COMPLETE("tool.complete"),

    /**
     * 证据引用
     */
    CITATIONS("citations"),

    /**
     * 审批请求
     */
    APPROVAL_REQUEST("approval.request"),

    /**
     * 审批撤回（超时）
     */
    APPROVAL_CANCEL("approval.cancel"),

    /**
     * 本轮结束
     */
    MESSAGE_COMPLETE("message.complete"),

    /**
     * 错误
     */
    CHAT_ERROR("chat.error");

    private final String type;

    SSEEventType(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }
}
