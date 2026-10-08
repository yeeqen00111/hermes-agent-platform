package com.hermes.agent.approval;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hermes.agent.common.enums.ApprovalChoice;
import com.hermes.agent.mapper.ApprovalRequestMapper;
import com.hermes.agent.mapper.ApprovalWhitelistMapper;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import static org.assertj.core.api.Assertions.assertThat;

class ApprovalServiceTest {

    private SingleConnectionDataSource dataSource;
    private SqlSession sqlSession;
    private JdbcTemplate jdbc;
    private ApprovalService service;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        new ResourceDatabasePopulator(new ClassPathResource("db/schema-sqlite.sql")).execute(dataSource);
        jdbc = new JdbcTemplate(dataSource);
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setLocalCacheScope(LocalCacheScope.STATEMENT);
        configuration.addMapper(ApprovalRequestMapper.class);
        configuration.addMapper(ApprovalWhitelistMapper.class);
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(configuration);
        sqlSession = factory.getObject().openSession(true);
        service = new ApprovalService(sqlSession.getMapper(ApprovalRequestMapper.class),
                sqlSession.getMapper(ApprovalWhitelistMapper.class), new ObjectMapper(), 1);
    }

    @AfterEach
    void tearDown() {
        if (sqlSession != null) {
            sqlSession.close();
        }
        if (dataSource != null) {
            dataSource.destroy();
        }
    }

    @ParameterizedTest
    @CsvSource({"alert.suppress,7", "alert.suppress,0", "notification.send,7", "notification.send,0",
            "mcp.demo.write,7", "mcp.demo.write,0"})
    void legacyNonIdempotentWhitelistCannotPreApproveButRemainsAuditable(String toolCode, long userId) {
        insertWhitelist(toolCode, userId, 1);

        assertThat(service.isWhitelisted(toolCode, 7L)).isFalse();
        assertThat(service.isPreApproved(toolCode, "session-a", 7L)).isFalse();
        assertThat(service.listWhitelist()).singleElement().satisfies(row -> {
            assertThat(row.getToolCode()).isEqualTo(toolCode);
            assertThat(row.getEnabled()).isEqualTo(1);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"alert.acknowledge", "alert.resolve"})
    void idempotentWhitelistHonorsUserAndEnabledState(String toolCode) {
        insertWhitelist(toolCode, 7L, 1);

        assertThat(service.isPreApproved(toolCode, "session-a", 7L)).isTrue();
        assertThat(service.isPreApproved(toolCode, "session-b", 7L)).isTrue();
        assertThat(service.isWhitelisted(toolCode, 8L)).isFalse();
        assertThat(service.isWhitelisted(toolCode, null)).isFalse();
        service.revokeWhitelist(service.listWhitelist().getFirst().getId());
        assertThat(service.isWhitelisted(toolCode, 7L)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"alert.acknowledge", "alert.resolve"})
    void idempotentGlobalWhitelistStillApplies(String toolCode) {
        insertWhitelist(toolCode, 0L, 1);

        assertThat(service.isWhitelisted(toolCode, 7L)).isTrue();
        assertThat(service.isWhitelisted(toolCode, 8L)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"alert.suppress", "notification.send", "mcp.demo.write"})
    void nonIdempotentAlwaysChoiceBecomesOnceAndDoesNotGrantWhitelist(String toolCode) {
        insertRequest("request-a", toolCode, "session-a");

        assertThat(service.respond("request-a", "allow_always", 7L, null)).isEqualTo(ApprovalChoice.ALLOW_ONCE);
        assertThat(service.findByRequestId("request-a").getChoice()).isEqualTo("allow_once");
        assertThat(service.findByRequestId("request-a").getStatus()).isEqualTo("ALLOWED");
        assertThat(service.listWhitelist()).isEmpty();
        assertThat(service.isPreApproved(toolCode, "session-a", 7L)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"alert.acknowledge", "alert.resolve"})
    void idempotentAlwaysChoicePersistsOnce(String toolCode) {
        insertRequest("request-a", toolCode, "session-a");

        assertThat(service.respond("request-a", "allow_always", 7L, null)).isEqualTo(ApprovalChoice.ALLOW_ALWAYS);
        assertThat(service.isPreApproved(toolCode, "session-b", 7L)).isTrue();
        assertThat(service.respond("request-a", "allow_always", 7L, null)).isNull();
        assertThat(service.listWhitelist()).hasSize(1);
    }

    @Test
    void nonIdempotentSessionGrantStaysWithinTheSession() {
        insertRequest("request-a", "alert.suppress", "session-a");

        assertThat(service.respond("request-a", "allow_session", 7L, null)).isEqualTo(ApprovalChoice.ALLOW_SESSION);
        assertThat(service.isPreApproved("alert.suppress", "session-a", 7L)).isTrue();
        assertThat(service.isPreApproved("alert.suppress", "session-b", 7L)).isFalse();
        assertThat(service.listWhitelist()).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "approve", "unknown"})
    void invalidChoiceDeniesWithoutGranting(String rawChoice) {
        insertRequest("request-a", "alert.acknowledge", "session-a");

        assertThat(service.respond("request-a", rawChoice, 7L, null)).isEqualTo(ApprovalChoice.DENY);
        assertThat(service.findByRequestId("request-a").getStatus()).isEqualTo("DENIED");
        assertThat(service.isPreApproved("alert.acknowledge", "session-a", 7L)).isFalse();
        assertThat(service.listWhitelist()).isEmpty();
    }

    private void insertWhitelist(String toolCode, long userId, int enabled) {
        jdbc.update("INSERT INTO ai_approval_whitelist (tool_code, user_id, granted_by, enabled) VALUES (?, ?, ?, ?)",
                toolCode, userId, 7L, enabled);
    }

    private void insertRequest(String requestId, String toolCode, String sessionId) {
        jdbc.update("INSERT INTO ai_approval_request (request_id, tool_code, session_id, user_id, status) "
                        + "VALUES (?, ?, ?, ?, 'PENDING')", requestId, toolCode, sessionId, 7L);
    }
}
