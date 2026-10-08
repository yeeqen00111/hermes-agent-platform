package com.hermes.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hermes.agent.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Agent配置实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_agent_profile")
public class AgentProfile extends BaseEntity {

    /**
     * Agent编码（唯一）
     */
    private String agentCode;

    /**
     * Agent名称
     */
    private String name;

    /**
     * 描述
     */
    private String description;

    /**
     * 系统提示（SOUL.MD正文）
     */
    private String systemPrompt;

    /**
     * 模型供应商
     */
    private String modelProvider;

    /**
     * 模型名称
     */
    private String modelName;

    /**
     * 温度
     */
    private Double temperature;

    /**
     * 最大Token数
     */
    private Integer maxTokens;

    /**
     * 启用的工具列表（JSON）
     */
    private String enabledTools;

    /**
     * 工具策略（JSON）
     */
    private String toolPolicy;

    /**
     * 超时时间(ms)
     */
    private Integer timeoutMs;

    /**
     * 记忆策略（JSON）
     */
    private String memoryPolicy;

    /**
     * 关联的知识库ID列表（JSON）
     */
    private String knowledgeBaseIds;

    /**
     * 启用的技能列表（JSON）
     */
    private String enabledSkills;

    /**
     * 启用的指令列表（JSON）
     */
    private String enabledCommands;

    /**
     * 挂载的MCP服务器（JSON）
     */
    private String enabledMcpServers;

    /**
     * 绑定的渠道（JSON）
     */
    private String channelBindings;

    /**
     * 数据范围策略（JSON）
     */
    private String dataScopePolicy;

    /** 灰度目标版本号（NULL=未开灰度），ADR-009 §8.2 */
    private Integer grayVersion;

    /** 灰度比例 0-100：新会话按该比例指向 grayVersion */
    private Integer grayRatio;

    /**
     * 执行模式: LLM_DRIVEN/FIXED_FLOW
     */
    private String executionMode;

    /**
     * 固定流程定义（JSON）
     */
    private String flowDefinition;

    /**
     * 触发方式: CHAT/API/SCHEDULED/WEBHOOK
     */
    private String triggerType;

    /**
     * 状态: DRAFT/PUBLISHED/DEPRECATED
     */
    private String status;

    /**
     * 当前发布版本号
     */
    private Integer currentVersion;
}
