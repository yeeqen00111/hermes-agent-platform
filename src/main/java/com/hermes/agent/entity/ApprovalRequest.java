package com.hermes.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工具审批请求（契约 §3.3）
 */
@Data
@TableName("ai_approval_request")
public class ApprovalRequest {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 对外暴露的审批单号 */
    private String requestId;

    private String sessionId;

    private String agentCode;

    private String traceId;

    private Long userId;

    private String toolCode;

    private String toolName;

    private String safetyLevel;

    /** 工具参数JSON（已脱敏） */
    private String arguments;

    /** PENDING/ALLOWED/DENIED/TIMEOUT/CANCELLED */
    private String status;

    /** allow_once/allow_session/allow_always/deny */
    private String choice;

    private Long decidedBy;

    private String reason;

    private LocalDateTime createTime;

    private LocalDateTime expireTime;

    private LocalDateTime decideTime;
}
