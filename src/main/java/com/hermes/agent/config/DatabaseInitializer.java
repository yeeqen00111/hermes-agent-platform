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
                preClean(connection);
                ScriptUtils.executeSqlScript(connection, resource);
                migrate(connection);
                log.info("SQLite数据库表初始化完成");
            } catch (Exception e) {
                log.error("数据库初始化失败", e);
                throw e;
            }
        } else {
            log.info("非SQLite数据库，跳过自动建表（请手动执行schema.sql）");
        }
    }

    /**
     * 建表/种子之前的清理（历史库修正，全新库无副作用、语句失败即忽略）：
     * 身份文件曾被种子重复插入，且软删行会与唯一索引下的补插冲突 →
     * 先物理清除软删行、再按 (scope, agent_code, file_type) 归并，最后建唯一索引，
     * 使随后的种子 INSERT OR IGNORE 真正幂等且能补回缺失行。
     */
    private void preClean(Connection connection) {
        String[] statements = {
                "DELETE FROM ai_agent_context_file WHERE del_flag <> 0",
                "DELETE FROM ai_agent_context_file WHERE id NOT IN "
                        + "(SELECT MIN(id) FROM ai_agent_context_file GROUP BY scope, agent_code, file_type)",
                "CREATE UNIQUE INDEX IF NOT EXISTS uk_agent_context_scope_agent_type "
                        + "ON ai_agent_context_file(scope, agent_code, file_type)"
        };
        for (String sql : statements) {
            try (var stmt = connection.createStatement()) {
                stmt.execute(sql);
            } catch (java.sql.SQLException e) {
                log.debug("预清理语句跳过（表未建或已处理）: {}", e.getMessage());
            }
        }
    }

    /**
     * 旧库增量迁移：加列语句幂等执行，列已存在则忽略
     */
    private void migrate(Connection connection) {
        String[] migrations = {
                "ALTER TABLE ai_agent_profile ADD COLUMN execution_mode VARCHAR(32) DEFAULT 'LLM_DRIVEN'",
                "ALTER TABLE ai_agent_profile ADD COLUMN flow_definition TEXT",
                "ALTER TABLE ai_agent_profile ADD COLUMN trigger_type VARCHAR(32) DEFAULT 'CHAT'",
                "ALTER TABLE ai_agent_context_file ADD COLUMN agent_code VARCHAR(64)",
                "ALTER TABLE ai_mcp_server ADD COLUMN create_by VARCHAR(64)",
                "ALTER TABLE ai_mcp_server ADD COLUMN update_by VARCHAR(64)",
                "ALTER TABLE ai_mcp_tool ADD COLUMN del_flag TINYINT DEFAULT 0",
                "ALTER TABLE ai_mcp_tool ADD COLUMN create_by VARCHAR(64)",
                "ALTER TABLE ai_mcp_tool ADD COLUMN update_by VARCHAR(64)",
                "ALTER TABLE ai_channel ADD COLUMN create_by VARCHAR(64)",
                "ALTER TABLE ai_channel ADD COLUMN update_by VARCHAR(64)",
                "ALTER TABLE ai_chat_session ADD COLUMN model_override VARCHAR(128)",
                "ALTER TABLE ai_agent_profile ADD COLUMN gray_version INT",
                "ALTER TABLE ai_agent_profile ADD COLUMN gray_ratio INT DEFAULT 0",
                "ALTER TABLE ai_channel ADD COLUMN config TEXT",
                // 模型档位映射（白板：DeepSeek Pro / DeepSeek Flash / GLM）
                "ALTER TABLE ai_model ADD COLUMN display_name VARCHAR(64)",
                "ALTER TABLE ai_model ADD COLUMN tier VARCHAR(16)",
                "UPDATE ai_model SET display_name='DeepSeek Flash', tier='FLASH' WHERE provider_code='deepseek' "
                        + "AND model_name='deepseek-chat' AND (display_name IS NULL OR display_name='')",
                "UPDATE ai_model SET display_name='DeepSeek Pro', tier='PRO' WHERE provider_code='deepseek' "
                        + "AND model_name='deepseek-reasoner' AND (display_name IS NULL OR display_name='')",
                "UPDATE ai_model SET display_name='GLM Flash', tier='STANDARD' WHERE provider_code='glm' "
                        + "AND model_name='glm-4-flash' AND (display_name IS NULL OR display_name='')"
        };
        for (String sql : migrations) {
            try (var stmt = connection.createStatement()) {
                stmt.execute(sql);
            } catch (java.sql.SQLException e) {
                if (!e.getMessage().toLowerCase().contains("duplicate column")) {
                    log.warn("迁移语句执行失败: {} -> {}", sql, e.getMessage());
                }
            }
        }
    }
}
