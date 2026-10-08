package com.hermes.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hermes.agent.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * nacos 配置分类 / 服务分类（白板·系统管理）：
 * 让 nacos 的配置、服务与系统（项目）挂钩，供 HERMES 与运维助手按系统维度查看/授权。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_nacos_category")
public class NacosCategory extends BaseEntity {

    private String code;

    private String name;

    /** CONFIG（配置分类）/ SERVICE（服务分类） */
    private String categoryType;

    /** 关联 nacos 服务器编码 */
    private String serverCode;

    /** 挂钩的系统 */
    private String systemName;

    /** nacos group */
    private String groupName;

    /** 匹配模式（data_id / 服务名前缀） */
    private String matchPattern;

    private Integer enabled;

    private String remark;
}
