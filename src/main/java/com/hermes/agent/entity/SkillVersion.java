package com.hermes.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 技能版本快照实体
 */
@Data
@TableName("ai_skill_version")
public class SkillVersion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String skillCode;

    private Integer version;

    /**
     * SKILL.md全文
     */
    private String content;

    /**
     * 依赖的工具列表（JSON）
     */
    private String requiresTools;

    private String author;

    private LocalDateTime createTime;
}
