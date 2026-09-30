package com.hermes.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工具调用审计（契约 §17.1 所有动作可审计），只写只读
 */
@Data
@TableName("ai_tool_call")
public class ToolCall {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String traceId;

    private String sessionId;

    private String agentCode;

    private String toolCode;

    private String safetyLevel;

    /** 工具参数JSON（已脱敏） */
    private String arguments;

    /** 1=成功 0=失败 */
    private Integer success;

    private String errorCode;

    private Long durationMs;

    /** allow_once/allow_session/allow_always/deny/timeout/cancelled；免审为 session_grant/whitelist/required */
    private String approvalChoice;

    private Long approvalBy;

    private LocalDateTime approvalTime;

    /** chat / flow / review */
    private String channel;

    private LocalDateTime createTime;
}
