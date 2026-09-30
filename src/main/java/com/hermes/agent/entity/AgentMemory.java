package com.hermes.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hermes.agent.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 记忆存储（三层：AGENT/USER/SESSION）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_agent_memory")
public class AgentMemory extends BaseEntity {

    private String agentCode;

    /** AGENT/USER/SESSION */
    private String scope;

    /** scope=USER时为userId，scope=SESSION时为sessionId，AGENT为空串 */
    private String scopeKey;

    private String memoryKey;

    private String content;

    /** 来源: SYSTEM/USER/LLM_WRITEBACK */
    private String source;

    private Integer hitCount;

    private java.time.LocalDateTime lastHitTime;
}
