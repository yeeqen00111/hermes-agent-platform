package com.hermes.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hermes.agent.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户画像（USER身份层）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_agent_user_profile")
public class AgentUserProfile extends BaseEntity {

    private Long userId;

    /** 用户角色/职责/偏好描述 */
    private String profileText;

    /** 偏好（JSON） */
    private String preferences;

    /** 默认数据范围（JSON） */
    private String dataScope;
}
