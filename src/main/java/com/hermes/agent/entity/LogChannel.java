package com.hermes.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hermes.agent.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 日志采集通道（白板·系统管理）：配置从哪采集日志。
 * 通道类型：KAFKA（目前唯一支持）/ MQ（消息队列）/ FILEBEAT_JSON（兼容 filebeat.log）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_log_channel")
public class LogChannel extends BaseEntity {

    private String code;

    private String name;

    /** KAFKA / MQ / FILEBEAT_JSON */
    private String channelType;

    /** JSON：KAFKA={brokers,topic,groupId,securityProtocol}；MQ={topic}；FILEBEAT_JSON={path} */
    private String config;

    private Integer enabled;

    private String remark;
}
