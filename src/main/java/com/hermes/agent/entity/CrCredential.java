package com.hermes.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hermes.agent.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 代码仓库凭据：只存引用，不存明文
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cr_credential")
public class CrCredential extends BaseEntity {

    private String credCode;

    private String name;

    /** GIT_TOKEN/GIT_PASSWORD/API_KEY */
    private String credType;

    private String username;

    /** 环境变量名/密钥管理器键 */
    private String secretRef;

    private Integer enabled;
}
