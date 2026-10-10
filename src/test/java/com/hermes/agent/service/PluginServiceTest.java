package com.hermes.agent.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hermes.agent.entity.AgentProfile;
import com.hermes.agent.entity.AiPlugin;
import com.hermes.agent.mapper.AgentProfileMapper;
import com.hermes.agent.mapper.AiPluginMapper;
import com.hermes.agent.plugin.PluginRegistry;
import com.hermes.agent.skill.SkillRegistry;
import com.hermes.agent.tool.ToolRegistry;
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
 * 插件机制测试：装载（绑定∩启用）、能力并集（技能/工具/提示词）、启停即时生效、核验。
 */
class PluginServiceTest {

    private SingleConnectionDataSource dataSource;
    private SqlSession sqlSession;
    private AiPluginMapper pluginMapper;
    private PluginRegistry registry;
    private PluginService service;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        new ResourceDatabasePopulator(new ClassPathResource("db/schema-sqlite.sql")).execute(dataSource);
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setLocalCacheScope(LocalCacheScope.STATEMENT);
        configuration.addMapper(AiPluginMapper.class);
        configuration.addMapper(com.hermes.agent.mapper.AiAgentPluginMapper.class);
        configuration.addMapper(AgentProfileMapper.class);
        configuration.addMapper(com.hermes.agent.mapper.SkillMapper.class);
        configuration.addMapper(com.hermes.agent.mapper.SkillVersionMapper.class);
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(configuration);
        sqlSession = factory.getObject().openSession(true);

        pluginMapper = sqlSession.getMapper(AiPluginMapper.class);
        ToolRegistry toolRegistry = new ToolRegistry();
        // 平台内置只读工具桩（对应运行时的 log.search / alert.query）
        for (String code : List.of("log.search", "alert.query")) {
            com.hermes.agent.tool.ToolDefinition def = new com.hermes.agent.tool.ToolDefinition();
            def.setToolCode(code);
            def.setSafetyLevel(com.hermes.agent.common.enums.SafetyLevel.READ);
            def.setEnabled(true);
            toolRegistry.register(def);
        }

        registry = new PluginRegistry(pluginMapper,
                sqlSession.getMapper(com.hermes.agent.mapper.AiAgentPluginMapper.class),
                toolRegistry,
                new SkillRegistry(sqlSession.getMapper(com.hermes.agent.mapper.SkillMapper.class),
                        sqlSession.getMapper(com.hermes.agent.mapper.SkillVersionMapper.class)),
                new ObjectMapper());
        service = new PluginService(pluginMapper,
                sqlSession.getMapper(com.hermes.agent.mapper.AiAgentPluginMapper.class),
                sqlSession.getMapper(AgentProfileMapper.class),
                registry);
        registry.reload();
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

    private AiPlugin plugin(String code, String name, String manifest, Integer enabled, String status) {
        AiPlugin p = new AiPlugin();
        p.setPluginCode(code);
        p.setName(name);
        p.setVersion("1.0.0");
        p.setPluginType("CAPABILITY");
        p.setManifest(manifest);
        p.setEnabled(enabled);
        p.setStatus(status);
        pluginMapper.insert(p);
        return p;
    }

    @Test
    void seededPluginLoadsButHasNoEffectWithoutBinding() {
        List<PluginRegistry.Plugin> loaded = registry.loadedPlugins();
        assertThat(loaded).extracting(PluginRegistry.Plugin::pluginCode).contains("ops-triage-pack");
        // 未绑定 → 对任意智能体都不生效
        assertThat(registry.pluginsForAgent("assistant")).isEmpty();
        assertThat(registry.promptBlocks("assistant")).isEmpty();
        assertThat(registry.toolCodesForAgent("assistant")).isEmpty();
    }

    @Test
    void bindingActivePluginContributesCapabilities() {
        Map<String, Object> r = service.bind("assistant", "ops-triage-pack");
        assertThat(r).containsEntry("success", true).containsEntry("active", true);

        assertThat(registry.pluginsForAgent("assistant")).hasSize(1);
        assertThat(registry.toolCodesForAgent("assistant")).contains("log.search", "alert.query");
        assertThat(registry.promptBlocks("assistant")).hasSize(1);
        assertThat(registry.promptBlocks("assistant").get(0)).contains("ops-triage-pack");
    }

    @Test
    void disablingPluginRemovesItsContribution() {
        service.bind("assistant", "ops-triage-pack");
        assertThat(registry.promptBlocks("assistant")).isNotEmpty();

        AiPlugin p = pluginMapper.selectOne(new LambdaQueryWrapper<AiPlugin>()
                .eq(AiPlugin::getPluginCode, "ops-triage-pack"));
        service.setEnabled(p.getId(), false);

        // 停用即卸载：绑定仍在但不生效
        assertThat(registry.loadedPlugins()).isEmpty();
        assertThat(registry.pluginsForAgent("assistant")).isEmpty();
        assertThat(registry.promptBlocks("assistant")).isEmpty();
    }

    @Test
    void unbindRemovesContribution() {
        service.bind("assistant", "ops-triage-pack");
        service.unbind("assistant", "ops-triage-pack");
        assertThat(registry.pluginsForAgent("assistant")).isEmpty();
    }

    @Test
    void malformedManifestPluginIsSkippedAndReported() {
        plugin("bad-json", "坏清单", "{not-json", 1, "ACTIVE");
        assertThat(registry.loadedPlugins()).extracting(PluginRegistry.Plugin::pluginCode)
                .doesNotContain("bad-json");

        assertThat(registry.verify("bad-json")).isNotEmpty();
        Map<String, Object> verify = service.verify();
        assertThat(verify).containsEntry("ok", false);
        @SuppressWarnings("unchecked")
        List<String> issues = (List<String>) verify.get("issues");
        assertThat(issues).anyMatch(i -> i.contains("bad-json"));
    }

    @Test
    void verifyFlagsUnregisteredToolInManifest() {
        plugin("ghost-tool", "幽灵工具",
                "{\"tools\":[\"log.search\",\"not.registered\"],\"prompt\":\"x\"}", 1, "ACTIVE");

        assertThat(registry.verify("ghost-tool"))
                .containsExactly("工具未注册: not.registered");
        Map<String, Object> verify = service.verify();
        assertThat(verify).containsEntry("ok", false);
    }

    @Test
    void verifyCleanForSeededCatalog() {
        Map<String, Object> verify = service.verify();
        assertThat(verify).containsEntry("ok", true);
        assertThat(verify).containsEntry("bindingCount", 0);
    }

    @Test
    void bindUnknownAgentOrPluginRejected() {
        assertThat(catchThrowable(() -> service.bind("no-such-agent", "ops-triage-pack")))
                .contains("智能体不存在");
        plugin("p1", "p1", "{}", 1, "ACTIVE");
        assertThat(catchThrowable(() -> service.bind("assistant", "no-such-plugin")))
                .contains("插件不存在");
    }

    @Test
    void bindingToDisabledPluginIsReportedByVerify() {
        AiPlugin p = plugin("paused", "暂停插件", "{\"prompt\":\"y\"}", 0, "DISABLED");
        // 直接插绑定（绕过 bind 的幂等路径），模拟历史数据
        com.hermes.agent.entity.AiAgentPlugin b = new com.hermes.agent.entity.AiAgentPlugin();
        b.setAgentCode("assistant");
        b.setPluginCode("paused");
        b.setEnabled(1);
        sqlSession.getMapper(com.hermes.agent.mapper.AiAgentPluginMapper.class).insert(b);

        Map<String, Object> verify = service.verify();
        assertThat(verify).containsEntry("ok", false);
        @SuppressWarnings("unchecked")
        List<String> issues = (List<String>) verify.get("issues");
        assertThat(issues).anyMatch(i -> i.contains("paused"));
        assertThat(p.getId()).isNotNull();
    }

    @Test
    void deletePluginClearsBindings() {
        AiPlugin p = pluginMapper.selectOne(new LambdaQueryWrapper<AiPlugin>()
                .eq(AiPlugin::getPluginCode, "ops-triage-pack"));
        service.bind("assistant", "ops-triage-pack");
        service.delete(p.getId());

        assertThat(service.list()).isEmpty();
        Map<String, Object> verify = service.verify();
        assertThat(verify).containsEntry("bindingCount", 0);
        assertThat(verify).containsEntry("ok", true);
    }

    @Test
    void pluginsForAgentReportsEffectiveState() {
        service.bind("assistant", "ops-triage-pack");
        AiPlugin p = pluginMapper.selectOne(new LambdaQueryWrapper<AiPlugin>()
                .eq(AiPlugin::getPluginCode, "ops-triage-pack"));
        service.setEnabled(p.getId(), false);

        List<Map<String, Object>> rows = service.pluginsForAgent("assistant");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("pluginCode", "ops-triage-pack")
                .containsEntry("pluginStatus", "DISABLED");
    }

    @SuppressWarnings("unchecked")
    private List<AgentProfile> profiles() {
        return sqlSession.getMapper(AgentProfileMapper.class).selectList(null);
    }

    private String catchThrowable(Runnable r) {
        try {
            r.run();
            return "";
        } catch (IllegalArgumentException e) {
            return String.valueOf(e.getMessage());
        }
    }
}
