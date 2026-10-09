package com.hermes.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hermes.agent.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 租户（白板·系统层·租户管理）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_tenant")
public class SysTenant extends BaseEntity {

    private String code;

    private String name;

    private String contact;

    private String phone;

    /** ACTIVE / DISABLED */
    private String status;

    private String remark;
}
