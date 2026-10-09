package com.hermes.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hermes.agent.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 渠道配置：凭据归 Java 平台统一管理，这里只存引用（契约 §5.1 规矩1）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_channel")
public class AiChannel extends BaseEntity {

    private String channelCode;

    /** FEISHU / DINGTALK / WECOM */
    private String channelType;

    private String name;

    private String appId;

    /** 环境变量名/密钥管理器键，不存明文 */
    private String appSecretRef;

    /** 渠道扩展配置 JSON（钉钉/企微 agentId 等） */
    private String config;

    private Integer enabled;

    /** CONNECTED / CONNECTING / FAILED / DISCONNECTED */
    private String status;

    private String errorMessage;

    /** 持有连接的实例ID */
    private String ownerInstance;

    private LocalDateTime heartbeatTime;
}
