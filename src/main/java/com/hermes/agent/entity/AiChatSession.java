package com.hermes.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话实体（契约 §7.1：会话正文权威存储在智能体平台侧）。
 * 该表无 create_by/update_by 审计列，故不继承 BaseEntity。
 */
@Data
@TableName("ai_chat_session")
public class AiChatSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableLogic
    private Integer delFlag;

    private String sessionId;

    private Long userId;

    private String agentCode;

    private Integer agentVersion;

    private String title;

    /** 渠道来源: WEB/FEISHU/DINGTALK/WECOM */
    private String channel;

    /** 状态: ACTIVE/STOPPED */
    private String status;

    private LocalDateTime lastMessageTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
