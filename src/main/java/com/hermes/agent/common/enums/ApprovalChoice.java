package com.hermes.agent.common.enums;

/**
 * 审批选项
 */
public enum ApprovalChoice {
    /**
     * 允许本次调用
     */
    ALLOW_ONCE,

    /**
     * 允许本次会话内所有匹配调用
     */
    ALLOW_SESSION,

    /**
     * 始终允许（写入白名单）
     */
    ALLOW_ALWAYS,

    /**
     * 拒绝
     */
    DENY
}
