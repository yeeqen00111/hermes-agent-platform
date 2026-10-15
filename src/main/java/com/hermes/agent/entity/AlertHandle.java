package com.hermes.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 告警处理流水（追加式）：工具 alert.acknowledge/resolve/suppress 的落单，
 * 与「只写不改」的 ai_alert_record 分离——处理动作不改告警记录本身。
 */
@Data
@TableName("ai_alert_handle")
public class AlertHandle {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联 ai_alert_record.id */
    private Long recordId;

    /** CONFIRMED / RESOLVED / SUPPRESSED */
    private String action;

    private String comment;

    private String handleBy;

    private LocalDateTime createTime;
}
