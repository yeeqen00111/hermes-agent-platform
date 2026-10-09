package com.hermes.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hermes.agent.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 【AI】智能运维授权（白板·业务层·智能运维）：
 * 代码仓库授权 / nacos 配置授权 / 日志授权——即「运维助手可以查看哪些代码仓库 / nacos 配置 / 日志」。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_ops_grant")
public class OpsGrant extends BaseEntity {

    private String code;

    private String name;

    /** 被授权的智能体（运维助手） */
    private String agentCode;

    /** REPO（代码仓库）/ NACOS（nacos 配置）/ LOG（日志） */
    private String grantType;

    /** 资源引用（仓库编码 / nacos 服务器或配置分类 / 日志通道或系统） */
    private String resourceRef;

    /** READ / WRITE */
    private String permission;

    private Integer enabled;

    private String remark;
}
