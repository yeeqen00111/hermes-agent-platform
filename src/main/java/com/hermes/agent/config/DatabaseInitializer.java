package com.hermes.agent.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * 数据库初始化器（开发环境自动建表）
 */
@Slf4j
@Component
public class DatabaseInitializer implements ApplicationRunner {

    private final DataSource dataSource;

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    public DatabaseInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        // 只对SQLite执行自动建表
        if (datasourceUrl.startsWith("jdbc:sqlite:")) {
            log.info("检测到SQLite，执行自动建表...");
            try (Connection connection = dataSource.getConnection()) {
                ClassPathResource resource = new ClassPathResource("db/schema-sqlite.sql");
                ScriptUtils.executeSqlScript(connection, resource);
                log.info("SQLite数据库表初始化完成");
            } catch (Exception e) {
                log.error("数据库初始化失败", e);
                throw e;
            }
        } else {
            log.info("非SQLite数据库，跳过自动建表（请手动执行schema.sql）");
        }
    }
}
