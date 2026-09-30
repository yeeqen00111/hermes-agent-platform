package com.hermes.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hermes.agent.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 身份包上下文文件（SOUL/AGENTS等，Markdown正文）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_agent_context_file")
public class AgentContextFile extends BaseEntity {

    /** SOUL/AGENTS/MEMORY_POLICY/USER */
    private String fileType;

    private String content;

    /** GLOBAL/AGENT */
    private String scope;

    private String agentCode;

    private String environment;

    private Integer version;
}
