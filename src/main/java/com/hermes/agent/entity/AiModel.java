package com.hermes.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 模型目录（catalog，无审计列的字典表）。
 * 密钥不在本表——见 ai_model_provider.api_key_ref。
 */
@Data
@TableName("ai_model")
public class AiModel {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String providerCode;

    private String modelName;

    /** 展示名（白板口径：DeepSeek Pro / DeepSeek Flash / GLM） */
    private String displayName;

    /** 档位：PRO / FLASH / STANDARD */
    private String tier;

    private Integer contextWindow;

    private Integer supportsTools;

    private Integer enabled;
}
