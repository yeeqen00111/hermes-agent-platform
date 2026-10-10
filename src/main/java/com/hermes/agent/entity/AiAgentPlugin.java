package com.hermes.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hermes.agent.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 智能体与插件的绑定关系：绑定且双方启用时，插件能力随身份包生效。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_agent_plugin")
public class AiAgentPlugin extends BaseEntity {

    /** 智能体编码 */
    private String agentCode;

    /** 插件编码 */
    private String pluginCode;

    /** 绑定是否启用 */
    private Integer enabled;
}
