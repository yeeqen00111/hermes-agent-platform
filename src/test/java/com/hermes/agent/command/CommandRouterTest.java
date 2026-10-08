package com.hermes.agent.command;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hermes.agent.mapper.AgentProfileMapper;
import com.hermes.agent.mapper.AiChatMessageMapper;
import com.hermes.agent.mapper.AiChatSessionMapper;
import com.hermes.agent.mapper.AiCommandBundleMapper;
import com.hermes.agent.mapper.AiModelMapper;
import com.hermes.agent.session.SessionManager;
import com.hermes.agent.skill.SkillRegistry;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CommandRouterTest {

    private final CommandRouter.CommandContext context =
            new CommandRouter.CommandContext("session-a", "assistant", 7L);
    private SingleConnectionDataSource dataSource;
    private SqlSession sqlSession;
    private JdbcTemplate jdbc;
    private SessionManager sessions;
    private CommandRouter router;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        new ResourceDatabasePopulator(new ClassPathResource("db/schema-sqlite.sql")).execute(dataSource);
        jdbc = new JdbcTemplate(dataSource);
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setLocalCacheScope(LocalCacheScope.STATEMENT);
        configuration.addMapper(AiModelMapper.class);
        configuration.addMapper(AgentProfileMapper.class);
        configuration.addMapper(AiChatSessionMapper.class);
        configuration.addMapper(AiChatMessageMapper.class);
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(configuration);
        sqlSession = factory.getObject().openSession(true);
        ObjectMapper objectMapper = new ObjectMapper();
        sessions = new SessionManager(sqlSession.getMapper(AiChatSessionMapper.class),
                sqlSession.getMapper(AiChatMessageMapper.class), objectMapper);
        sessions.createSession(context.sessionId(), context.userId(), context.agentCode());
        router = new CommandRouter(mock(SkillRegistry.class), mock(AiCommandBundleMapper.class),
                sqlSession.getMapper(AgentProfileMapper.class), sqlSession.getMapper(AiModelMapper.class),
                sessions, objectMapper);
        ReflectionTestUtils.invokeMethod(router, "registerBuiltinCommands");
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

    @Test
    void contextReportsCatalogWindowInTokensWithoutDividingIt() {
        sessions.setModelOverride(context.sessionId(), "glm/glm-4-flash");

        CommandRouter.CommandResult result = router.route("/context", context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).contains("上下文窗口 128000 tokens")
                .doesNotContain("32000 tokens");
    }

    @Test
    void contextUsesBothProviderAndModelName() {
        insertModel("provider-a", "shared-model", 16000, 1);
        insertModel("provider-b", "shared-model", 96000, 1);
        sessions.setModelOverride(context.sessionId(), "provider-b/shared-model");

        assertThat(router.route("/context", context).getMessage())
                .contains("上下文窗口 96000 tokens").doesNotContain("16000");
    }

    @Test
    void missingProviderDoesNotBorrowAnotherProvidersWindow() {
        insertModel("provider-a", "shared-model", 16000, 1);
        sessions.setModelOverride(context.sessionId(), "missing/shared-model");

        assertThat(router.route("/context", context).getMessage())
                .contains("missing/shared-model").doesNotContain("上下文窗口");
    }

    @Test
    void disabledModelDoesNotBorrowAnotherProvidersWindow() {
        insertModel("provider-a", "shared-model", 16000, 1);
        insertModel("provider-b", "shared-model", 96000, 0);
        sessions.setModelOverride(context.sessionId(), "provider-b/shared-model");

        assertThat(router.route("/context", context).getMessage()).doesNotContain("上下文窗口");
    }

    @Test
    void modelSwitchAndResetPersistOnlyForTheSelectedSession() {
        sessions.createSession("session-b", 7L, "assistant");

        assertThat(router.route("/model glm/glm-4-flash", context).isSuccess()).isTrue();
        assertThat(sessions.getSession(context.sessionId()).orElseThrow().getModelOverride())
                .isEqualTo("glm/glm-4-flash");
        assertThat(sessions.getSession("session-b").orElseThrow().getModelOverride()).isNull();
        assertThat(router.route("/model reset", context).getMessage())
                .contains("deepseek/deepseek-chat（Agent 默认）");
        assertThat(sessions.getSession(context.sessionId()).orElseThrow().getModelOverride()).isNull();
        assertThat(router.route("/context", context).getMessage()).contains("上下文窗口 64000 tokens");
        assertThat(jdbc.queryForObject("SELECT model_name FROM ai_agent_profile WHERE agent_code = 'assistant'",
                String.class)).isEqualTo("deepseek-chat");
    }

    @Test
    void contextSeparatesHistoryCharactersFromModelWindow() {
        SessionManager.ChatMessage first = new SessionManager.ChatMessage();
        first.setMessageId("message-a");
        first.setRole("USER");
        first.setContent("你好ab");
        sessions.addMessage(context.sessionId(), first);
        SessionManager.ChatMessage second = new SessionManager.ChatMessage();
        second.setMessageId("message-b");
        second.setRole("ASSISTANT");
        sessions.addMessage(context.sessionId(), second);

        assertThat(router.route("/context", context).getMessage())
                .contains("2 条，合计 4 字符", "上下文窗口 64000 tokens", "不代表完整 token 占用");
    }

    @Test
    void contextWithoutSessionReturnsAnError() {
        CommandRouter.CommandResult result = router.route("/context");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("NO_SESSION");
    }

    @Test
    void unknownModelDoesNotChangeExistingOverride() {
        sessions.setModelOverride(context.sessionId(), "glm/glm-4-flash");

        assertThat(router.route("/model missing/model", context).getErrorCode()).isEqualTo("UNKNOWN_MODEL");
        assertThat(sessions.getSession(context.sessionId()).orElseThrow().getModelOverride())
                .isEqualTo("glm/glm-4-flash");
    }

    private void insertModel(String provider, String name, int window, int enabled) {
        jdbc.update("INSERT INTO ai_model (provider_code, model_name, context_window, enabled) VALUES (?, ?, ?, ?)",
                provider, name, window, enabled);
    }
}
