package com.hermes.agent.common.enums;

/**
 * 工具安全等级
 */
public enum SafetyLevel {
    /**
     * 只读操作，权限通过后直接执行
     */
    READ,

    /**
     * 受控操作，需要二次确认或特定权限
     */
    CONTROLLED,

    /**
     * 写操作，必须人工审批
     */
    WRITE,

    /**
     * 禁止操作，根本不提供此工具
     */
    FORBIDDEN
}
