package com.hermes.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知发送日志（只写，不改删），与 ai_tool_call 同模式
 */
@Data
@TableName("ai_notify_log")
public class AiNotifyLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String channelCode;

    private String channelType;

    private String recipient;

    private String title;

    private String content;

    /** SUCCESS / FAILED / NO_CHANNEL */
    private String status;

    private String error;

    private LocalDateTime createTime;
}
