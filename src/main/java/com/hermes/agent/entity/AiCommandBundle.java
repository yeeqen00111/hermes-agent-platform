package com.hermes.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hermes.agent.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 指令捆绑包实体：一条指令预载一串技能（§7.4）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_command_bundle")
public class AiCommandBundle extends BaseEntity {

    /** 捆绑包编码（即指令名） */
    private String bundleCode;

    private String name;

    private String description;

    /** 技能编码清单（JSON数组） */
    private String skillCodes;

    /** 状态: ENABLED/DISABLED */
    private String status;

    private Integer version;
}
