package com.hermes.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hermes.agent.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 告警/通知通道：飞书 webhook 或 邮件 SMTP（◆ 复用供应链控制塔，平台侧为集成位+本地回退）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_notify_channel")
public class AiNotifyChannel extends BaseEntity {

    private String code;

    private String name;

    /** FEISHU / EMAIL */
    private String channelType;

    /** JSON：FEISHU={webhookUrl,secret}；EMAIL={smtpHost,smtpPort,username,password,from,ssl} */
    private String config;

    private Integer enabled;
}
