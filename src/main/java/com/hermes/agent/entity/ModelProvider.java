package com.hermes.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 模型供应商：密钥只存引用
 */
@Data
@TableName("ai_model_provider")
public class ModelProvider {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String providerCode;

    private String name;

    private String baseUrl;

    /** 环境变量名/密钥管理器键，不存明文 */
    private String apiKeyRef;

    private Integer enabled;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
