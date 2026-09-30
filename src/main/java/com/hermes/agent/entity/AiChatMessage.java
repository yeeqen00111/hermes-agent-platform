package com.hermes.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话消息实体
 */
@Data
@TableName("ai_chat_message")
public class AiChatMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String sessionId;

    private String messageId;

    /** 角色: USER/ASSISTANT/SYSTEM */
    private String role;

    private String content;

    /** 工具调用记录（JSON） */
    private String toolCalls;

    /** 证据引用（JSON） */
    private String citations;

    private String traceId;

    private LocalDateTime createTime;
}
