package com.hermes.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hermes.agent.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 数据库连接信息（◆ 复用供应链控制塔，平台侧为配置位+测试连接，消费方为四期问数/报表）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_db_connection")
public class AiDbConnection extends BaseEntity {

    private String code;

    private String name;

    /** MYSQL / OCEANBASE / SQLITE */
    private String dbType;

    private String host;

    private Integer port;

    private String databaseName;

    private String username;

    private String password;

    private String status;
}
