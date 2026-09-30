package com.hermes.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hermes.agent.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 项目管理（白板系统层：项目名称/项目描述/所属项目）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_project")
public class SysProject extends BaseEntity {

    private String name;

    private String description;

    private Long parentId;

    private String status;
}
