package com.hermes.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hermes.agent.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * nacos 服务器 / 配置凭据（白板·系统管理）。
 * 凭据只存引用：{@code secretRef} 存环境变量名，库中无明文；对外输出过 sanitize。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_nacos_server")
public class NacosServer extends BaseEntity {

    private String code;

    private String name;

    /** nacos 地址：http://host:8848 */
    private String serverAddr;

    /** 命名空间 */
    private String namespaceId;

    private String username;

    /** 凭据引用（环境变量名，不存明文） */
    private String secretRef;

    private Integer enabled;

    private String remark;
}
