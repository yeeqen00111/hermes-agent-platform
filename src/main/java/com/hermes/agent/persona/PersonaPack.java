package com.hermes.agent.persona;

import com.hermes.agent.entity.AgentProfile;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 身份包：单一基座上区分功能智能体的全部身份配置。
 * SOUL/AGENTS 进入系统提示；MEMORY/USER 作为上下文块注入。
 */
@Data
@Builder
public class PersonaPack {

    private AgentProfile profile;

    private String soul;

    private String agentsContext;

    private List<String> memoryBlocks;

    private String userProfile;

    /** 组装后的系统提示 */
    private String systemPrompt;

    public String getAgentCode() {
        return profile.getAgentCode();
    }

    public boolean isFixedFlow() {
        return "FIXED_FLOW".equalsIgnoreCase(profile.getExecutionMode());
    }
}
