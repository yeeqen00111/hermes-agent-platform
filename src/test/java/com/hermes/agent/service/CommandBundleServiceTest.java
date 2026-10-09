package com.hermes.agent.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hermes.agent.entity.AiCommandBundle;
import com.hermes.agent.entity.Skill;
import com.hermes.agent.entity.SkillVersion;
import com.hermes.agent.mapper.AiCommandBundleMapper;
import com.hermes.agent.mapper.SkillMapper;
import com.hermes.agent.mapper.SkillVersionMapper;
import com.hermes.agent.skill.SkillRegistry;
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
 * 指令（捆绑包）管理测试：CRUD 默认值 + 技能拼装预览（含缺失技能回报）。
 */
class CommandBundleServiceTest {

    private SingleConnectionDataSource dataSource;
    private SqlSession sqlSession;
    private CommandBundleService service;
    private AiCommandBundleMapper bundleMapper;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        new ResourceDatabasePopulator(new ClassPathResource("db/schema-sqlite.sql")).execute(dataSource);
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setLocalCacheScope(LocalCacheScope.STATEMENT);
        configuration.addMapper(AiCommandBundleMapper.class);
        configuration.addMapper(SkillMapper.class);
        configuration.addMapper(SkillVersionMapper.class);
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(configuration);
        sqlSession = factory.getObject().openSession(true);

        bundleMapper = sqlSession.getMapper(AiCommandBundleMapper.class);
        SkillRegistry registry = new SkillRegistry(sqlSession.getMapper(SkillMapper.class),
                sqlSession.getMapper(SkillVersionMapper.class));
        service = new CommandBundleService(bundleMapper, registry, new ObjectMapper());
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

    private void skill(String code, int version, String content) {
        Skill s = new Skill();
        s.setSkillCode(code);
        s.setName(code);
        s.setDescription(code + " 说明");
        s.setStatus("ENABLED");
        s.setCurrentVersion(version);
        sqlSession.getMapper(SkillMapper.class).insert(s);

        SkillVersion v = new SkillVersion();
        v.setSkillCode(code);
        v.setVersion(version);
        v.setContent(content);
        sqlSession.getMapper(SkillVersionMapper.class).insert(v);
    }

    private AiCommandBundle bundle(String code, String skillCodes) {
        AiCommandBundle b = new AiCommandBundle();
        b.setBundleCode(code);
        b.setName(code);
        b.setDescription("测试捆绑包");
        b.setSkillCodes(skillCodes);
        return service.save(b);
    }

    @Test
    void saveAppliesDefaultsAndLists() {
        AiCommandBundle saved = bundle("oncall", "[\"log-triage\"]");
        assertThat(saved.getStatus()).isEqualTo("ENABLED");
        assertThat(saved.getVersion()).isEqualTo(1);
        assertThat(service.list()).extracting(AiCommandBundle::getBundleCode).containsExactly("oncall");
    }

    @Test
    @SuppressWarnings("unchecked")
    void previewAssemblesSkillContent() {
        skill("log-triage", 1, "排查步骤：先看日志");
        skill("alert-runbook", 2, "告警处置：确认-解决");
        AiCommandBundle b = bundle("oncall", "[\"log-triage\",\"alert-runbook\"]");

        Map<String, Object> out = service.preview(b.getId());

        assertThat(out).containsEntry("success", true).containsEntry("command", "/oncall");
        assertThat(String.valueOf(out.get("content"))).contains("排查步骤：先看日志").contains("告警处置：确认-解决");
        assertThat((List<Map<String, Object>>) out.get("skills")).hasSize(2);
        assertThat((List<?>) out.get("missing")).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void previewReportsMissingSkills() {
        skill("log-triage", 1, "正文");
        AiCommandBundle b = bundle("mixed", "[\"log-triage\",\"not-exist\"]");

        Map<String, Object> out = service.preview(b.getId());

        assertThat((List<String>) out.get("missing")).containsExactly("not-exist");
        assertThat((List<Map<String, Object>>) out.get("skills"))
                .extracting(r -> r.get("code") + "=" + r.get("loaded"))
                .containsExactly("log-triage=true", "not-exist=false");
    }

    @Test
    void previewUnknownBundleFails() {
        assertThat(service.preview(999L)).containsEntry("success", false);
    }

    @Test
    void deleteRemoves() {
        AiCommandBundle b = bundle("tmp", "[]");
        service.delete(b.getId());
        assertThat(service.list()).isEmpty();
    }
}
