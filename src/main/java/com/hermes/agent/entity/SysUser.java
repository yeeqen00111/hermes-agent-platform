package com.hermes.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hermes.agent.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户/人员管理（白板系统层人员管理，与用户合并）。
 * 角色/菜单权威在 ◆ 供应链控制塔；role_codes 仅作本地回退位。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseEntity {

    private String userCode;

    private String name;

    private String email;

    private String feishu;

    private String phone;

    private String roleCodes;

    private Integer isAdmin;

    private String status;
}
