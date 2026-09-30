package com.hermes.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 渠道用户映射（纯映射表，契约 §5.2）：open_id → 平台 userId
 */
@Data
@TableName("ai_channel_user")
public class AiChannelUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String channelCode;

    private String channelUserId;

    private Long platformUserId;

    private LocalDateTime pairedTime;

    /** SELF / ADMIN / IMPORT（配对方式待定，契约 §9 第 12 条） */
    private String pairedBy;
}
