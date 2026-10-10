package com.hermes.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hermes.agent.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 插件（补充项「插件」）：自有进程内注册的能力包，非外部 ABI。
 * 能力清单在 {@code manifest}：{"tools":[],"skills":[],"commands":[],"prompt":""}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_plugin")
public class AiPlugin extends BaseEntity {

    /** 插件编码（唯一） */
    private String pluginCode;

    /** 插件名称 */
    private String name;

    /** 插件版本 */
    private String version;

    /** 类型: CAPABILITY/PROMPT/CHANNEL */
    private String pluginType;

    /** 描述 */
    private String description;

    /** 能力清单（JSON） */
    private String manifest;

    /** 插件配置（JSON） */
    private String config;

    /** 是否启用 */
    private Integer enabled;

    /** 状态: ACTIVE/DISABLED */
    private String status;
}
