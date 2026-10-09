package com.hermes.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hermes.agent.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 【AI】智能告警报表（白板·业务层·智能运维）：
 * 发送频率（多久发送一次）/ 发送内容（范围）/ 提示词 / 发送通道。
 * 定时按频率聚合告警记录 → 交 LLM 生成 markdown → 经通知网关发送；支持人工选范围触发。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_alert_report")
public class AlertReport extends BaseEntity {

    private String code;

    private String name;

    /** 发送频率（小时，默认 2） */
    private Integer frequencyHours;

    /** 发送内容范围 JSON：projectName/alertType/level/lookbackHours */
    private String scope;

    /** 提示词 */
    private String prompt;

    /** 发送通道 */
    private String channelCode;

    private String recipient;

    private LocalDateTime lastSendTime;

    /** 最近一次生成的报表正文（markdown） */
    private String lastContent;

    private Integer enabled;

    private String remark;
}
