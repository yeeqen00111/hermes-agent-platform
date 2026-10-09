package com.hermes.agent.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.hermes.agent.entity.AgentProfile;
import com.hermes.agent.entity.AiModel;
import com.hermes.agent.mapper.AgentProfileMapper;
import com.hermes.agent.mapper.AiModelMapper;
import com.hermes.agent.mapper.ModelProviderMapper;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 大模型型号映射核验测试：目录↔供应商、档位完整性、Agent 引用模型与工具支持。
 * 模型/供应商来自 schema 种子，测试只做「新增新档位」或「改种子状态」，避免撞唯一键。
 */
class ModelCatalogServiceTest {

    private SingleConnectionDataSource dataSource;
    private SqlSession sqlSession;
    private ModelCatalogService service;
    private AiModelMapper modelMapper;
    private AgentProfileMapper profileMapper;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        new ResourceDatabasePopulator(new ClassPathResource("db/schema-sqlite.sql")).execute(dataSource);
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setLocalCacheScope(LocalCacheScope.STATEMENT);
        configuration.addMapper(AiModelMapper.class);
        configuration.addMapper(ModelProviderMapper.class);
        configuration.addMapper(AgentProfileMapper.class);
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(configuration);
        sqlSession = factory.getObject().openSession(true);

        modelMapper = sqlSession.getMapper(AiModelMapper.class);
        profileMapper = sqlSession.getMapper(AgentProfileMapper.class);
        service = new ModelCatalogService(modelMapper, sqlSession.getMapper(ModelProviderMapper.class),
                profileMapper);
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

    private void profile(String code, String provider, String name) {
        AgentProfile p = new AgentProfile();
        p.setAgentCode(code);
        p.setName(code);
        p.setModelProvider(provider);
        p.setModelName(name);
        p.setStatus("PUBLISHED");
        p.setExecutionMode("LLM_DRIVEN");
        profileMapper.insert(p);
    }

    private AiModel model(String provider, String name) {
        return modelMapper.selectOne(new LambdaQueryWrapper<AiModel>()
                .eq(AiModel::getProviderCode, provider).eq(AiModel::getModelName, name));
    }

    @SuppressWarnings("unchecked")
    @Test
    void seededCatalogVerifiesClean() {
        // 目录与 Agent 均来自种子（assistant 等用 deepseek/deepseek-chat），应无问题
        Map<String, Object> result = service.verify();

        assertThat(result).containsEntry("ok", true);
        assertThat((List<String>) result.get("issues")).isEmpty();
        assertThat((List<Map<String, Object>>) result.get("tiers")).hasSize(3);
        assertThat(result).containsEntry("agentCount", 4);
    }

    @SuppressWarnings("unchecked")
    @Test
    void missingRequiredTierReportedWhenProDisabled() {
        AiModel pro = model("deepseek", "deepseek-reasoner");
        pro.setEnabled(0);
        modelMapper.updateById(pro);

        Map<String, Object> result = service.verify();

        assertThat(result).containsEntry("ok", false);
        assertThat((List<String>) result.get("issues")).anyMatch(i -> i.contains("档位 PRO 无启用模型"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void modelWithoutTierReported() {
        AiModel extra = new AiModel();
        extra.setProviderCode("glm");
        extra.setModelName("glm-4-plus");
        extra.setDisplayName("GLM Plus");
        extra.setContextWindow(128000);
        extra.setSupportsTools(1);
        extra.setEnabled(1);
        modelMapper.insert(extra);

        Map<String, Object> result = service.verify();

        assertThat((List<String>) result.get("issues"))
                .anyMatch(i -> i.contains("glm/glm-4-plus") && i.contains("未标注档位"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void agentReferencingUnknownModelReported() {
        profile("smoke-agent-unknown", "deepseek", "no-such-model");

        Map<String, Object> result = service.verify();

        assertThat(result).containsEntry("ok", false);
        assertThat((List<String>) result.get("issues"))
                .anyMatch(i -> i.contains("引用的模型不在目录或未启用"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void agentUsingModelWithoutToolsReported() {
        AiModel glm = model("glm", "glm-4-flash");
        glm.setSupportsTools(0);
        modelMapper.updateById(glm);
        profile("smoke-agent-notools", "glm", "glm-4-flash");

        Map<String, Object> result = service.verify();

        assertThat((List<String>) result.get("issues")).anyMatch(i -> i.contains("不支持工具调用"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void modelWhoseProviderDisabledReported() {
        AiModel glm = model("glm", "glm-4-flash");
        // 供应商停用 → 其模型应被判为「供应商不存在或未启用」
        sqlSession.getMapper(ModelProviderMapper.class).delete(
                new LambdaQueryWrapper<com.hermes.agent.entity.ModelProvider>()
                        .eq(com.hermes.agent.entity.ModelProvider::getProviderCode, "glm"));

        Map<String, Object> result = service.verify();

        assertThat((List<String>) result.get("issues"))
                .anyMatch(i -> i.contains("glm/glm-4-flash") && i.contains("供应商不存在或未启用"));
        assertThat(glm.getId()).isNotNull();
    }
}
