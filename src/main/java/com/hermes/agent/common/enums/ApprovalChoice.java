package com.hermes.agent.common.enums;

/**
 * 审批应答（契约 §3.3）。
 * choice 为空一律按 DENY 处理；超时与撤回同样不放行（fail-closed）。
 */
public enum ApprovalChoice {

    ALLOW_ONCE(true),
    ALLOW_SESSION(true),
    ALLOW_ALWAYS(true),
    DENY(false),
    TIMEOUT(false),
    CANCELLED(false);

    private final boolean allowed;

    ApprovalChoice(boolean allowed) {
        this.allowed = allowed;
    }

    public boolean isAllowed() {
        return allowed;
    }

    public static ApprovalChoice fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return DENY;
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return DENY;
        }
    }

    /** 落库到 ai_approval_request.status */
    public String toStatus() {
        return switch (this) {
            case ALLOW_ONCE, ALLOW_SESSION, ALLOW_ALWAYS -> "ALLOWED";
            case DENY -> "DENIED";
            case TIMEOUT -> "TIMEOUT";
            case CANCELLED -> "CANCELLED";
        };
    }
}
