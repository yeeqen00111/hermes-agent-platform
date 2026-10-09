package com.hermes.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hermes.agent.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 菜单（白板·系统层·用户/角色/菜单）：树形，path 对应前端路由。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_menu")
public class SysMenu extends BaseEntity {

    private String code;

    private String name;

    /** 父菜单 ID（0=根） */
    private Long parentId;

    /** 前端路由路径 */
    private String path;

    private String icon;

    private Integer sortNo;

    /** ACTIVE / DISABLED */
    private String status;

    private String remark;
}
