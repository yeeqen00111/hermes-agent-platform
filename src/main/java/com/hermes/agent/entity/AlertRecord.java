package com.hermes.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 告警记录（白板·智能运维）：日志分级-固化流程的产出，供告警监控与告警发送日志取数。
 * 只写不改不删（与 ai_tool_call / ai_notify_log 同模式）。
 */
@Data
@TableName("ai_alert_record")
public class AlertRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String ruleCode;

    private String projectName;

    private String systemName;

    private String serverName;

    private String logLevel;

    private String content;

    private LocalDateTime logTime;

    /** ALERT / PROMOTE */
    private String alertType;

    /** SENT / FAILED / NO_CHANNEL / SUPPRESSED */
    private String status;

    private String channelCode;

    private String recipient;

    private LocalDateTime createTime;
}
