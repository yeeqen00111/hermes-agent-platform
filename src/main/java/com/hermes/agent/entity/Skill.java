package com.hermes.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hermes.agent.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 技能元数据实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_skill")
public class Skill extends BaseEntity {

    /**
     * 技能编码
     */
    private String skillCode;

    /**
     * 技能名称
     */
    private String name;

    /**
     * 技能描述（≤60字，进索引）
     */
    private String description;

    /**
     * 标签（JSON数组）
     */
    private String tags;

    /**
     * 依赖的工具列表（JSON数组）
     */
    private String requiresTools;

    /**
     * 状态: ENABLED/DISABLED
     */
    private String status;

    /**
     * 当前版本号
     */
    private Integer currentVersion;
}
