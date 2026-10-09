package com.hermes.agent.api;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.hermes.agent.entity.AiModel;
import com.hermes.agent.entity.ModelProvider;
import com.hermes.agent.mapper.AiModelMapper;
import com.hermes.agent.mapper.ModelProviderMapper;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 模型目录只读接口（契约 §3.5）。跑真 SQLite（项目约定），验证启用/工具调用/供应商过滤语义。
 */
class ModelControllerTest {

    private SingleConnectionDataSource dataSource;
    private SqlSession sqlSession;
    private JdbcTemplate jdbc;
    private ModelController controller;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        new ResourceDatabasePopulator(new ClassPathResource("db/schema-sqlite.sql")).execute(dataSource);
        jdbc = new JdbcTemplate(dataSource);
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setLocalCacheScope(LocalCacheScope.STATEMENT);
        configuration.addMapper(AiModelMapper.class);
        configuration.addMapper(ModelProviderMapper.class);
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(configuration);
        sqlSession = factory.getObject().openSession(true);
        controller = new ModelController(sqlSession.getMapper(AiModelMapper.class),
                sqlSession.getMapper(ModelProviderMapper.class), null);

        jdbc.update("DELETE FROM ai_model");
        jdbc.update("DELETE FROM ai_model_provider");
        jdbc.update("INSERT INTO ai_model_provider (provider_code,name,base_url,api_key_ref,enabled) "
                + "VALUES ('deepseek','DeepSeek','https://api.deepseek.com','DEEPSEEK_API_KEY',1)");
        jdbc.update("INSERT INTO ai_model_provider (provider_code,name,base_url,api_key_ref,enabled) "
                + "VALUES ('legacy','Legacy','http://legacy.local','LEGACY_API_KEY',0)");
        jdbc.update("INSERT INTO ai_model (provider_code,model_name,context_window,supports_tools,enabled) "
                + "VALUES ('deepseek','deepseek-chat',64000,1,1)");
        jdbc.update("INSERT INTO ai_model (provider_code,model_name,context_window,supports_tools,enabled) "
                + "VALUES ('deepseek','embed-only',8192,0,1)");
        jdbc.update("INSERT INTO ai_model (provider_code,model_name,context_window,supports_tools,enabled) "
                + "VALUES ('legacy','legacy-llm',4096,1,0)");
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

    private static List<String> names(List<AiModel> models) {
        return models.stream().map(AiModel::getModelName).toList();
    }

    @Test
    void defaultReturnsEnabledModelsOrderedByProviderThenName() {
        assertThat(names(controller.models(null, false, false)))
                .containsExactly("deepseek-chat", "embed-only");
    }

    @Test
    void toolsOnlyKeepsToolCapableModels() {
        assertThat(names(controller.models(null, true, false))).containsExactly("deepseek-chat");
    }

    @Test
    void includeDisabledAddsDisabledModels() {
        assertThat(names(controller.models(null, true, true))).containsExactly("deepseek-chat", "legacy-llm");
    }

    @Test
    void providerCodeFilterApplies() {
        assertThat(names(controller.models("deepseek", false, false)))
                .containsExactly("deepseek-chat", "embed-only");
        assertThat(names(controller.models("legacy", false, true))).containsExactly("legacy-llm");
    }

    @Test
    void providersDefaultEnabledOnlyAndIncludeDisabled() {
        assertThat(controller.providers(false)).extracting(ModelProvider::getProviderCode)
                .containsExactly("deepseek");
        assertThat(controller.providers(true)).extracting(ModelProvider::getProviderCode)
                .containsExactlyInAnyOrder("deepseek", "legacy");
    }
}
