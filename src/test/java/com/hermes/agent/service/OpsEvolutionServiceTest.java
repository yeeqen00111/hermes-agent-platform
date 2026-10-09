package com.hermes.agent.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.hermes.agent.entity.OpsExperience;
import com.hermes.agent.mapper.OpsExperienceMapper;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 运维智能体「自我进化」测试：记录归并 / 检索复用计数 / 反馈重算置信度 / 定时衰减淘汰 / 注入格式。
 */
class OpsEvolutionServiceTest {

    private SingleConnectionDataSource dataSource;
    private SqlSession sqlSession;
    private OpsEvolutionService service;
    private OpsExperienceMapper mapper;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        new ResourceDatabasePopulator(new ClassPathResource("db/schema-sqlite.sql")).execute(dataSource);
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setLocalCacheScope(LocalCacheScope.STATEMENT);
        configuration.addMapper(OpsExperienceMapper.class);
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(configuration);
        sqlSession = factory.getObject().openSession(true);
        mapper = sqlSession.getMapper(OpsExperienceMapper.class);
        service = new OpsEvolutionService(mapper);
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

    private OpsExperience capture(String problem, String solution, String tags) {
        OpsExperience e = new OpsExperience();
        e.setAgentCode("assistant");
        e.setProblem(problem);
        e.setSolution(solution);
        e.setTags(tags);
        e.setSourceType("ALERT");
        return service.capture(e);
    }

    @Test
    void captureInsertsNewExperienceWithHalfConfidence() {
        OpsExperience e = capture("订单接口 502 报错", "重启网关", "gateway,502");
        assertThat(e.getExpCode()).startsWith("exp-");
        assertThat(e.getConfidence()).isEqualTo(0.5);
        assertThat(e.getStatus()).isEqualTo("ACTIVE");
        assertThat(e.getProblemKey()).isEqualTo("订单接口502报错");
    }

    @Test
    void captureMergesSameProblemAndStrengthens() {
        OpsExperience first = capture("订单接口 502 报错", "重启网关", "gateway");
        OpsExperience merged = capture("订单接口502报错", "重启网关并扩容", "扩容");

        assertThat(service.list("assistant", null, null)).hasSize(1);
        assertThat(merged.getId()).isEqualTo(first.getId());
        assertThat(merged.getSolution()).isEqualTo("重启网关并扩容");
        assertThat(merged.getSuccessCount()).isEqualTo(1);
        assertThat(merged.getConfidence()).isGreaterThan(0.5);
        assertThat(merged.getTags()).contains("gateway").contains("扩容");
    }

    @Test
    void retrieveRanksByRelevanceAndBumpsHits() {
        capture("订单接口 502 报错", "重启网关", "gateway");
        capture("数据库连接池耗尽", "调大 maximumPoolSize", "database");

        List<Map<String, Object>> hits = service.retrieve("连接池", "assistant", 3);

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0)).containsEntry("problem", "数据库连接池耗尽");
        OpsExperience updated = mapper.selectList(new LambdaQueryWrapper<OpsExperience>()
                .eq(OpsExperience::getProblem, "数据库连接池耗尽")).get(0);
        assertThat(updated.getHits()).isEqualTo(1);
        assertThat(updated.getLastUsedTime()).isNotNull();
    }

    @Test
    void retrieveRanksMostRelevantFirst() {
        capture("订单接口 502 报错", "重启网关", "gateway");
        capture("数据库连接池耗尽", "调大 maximumPoolSize", "database");

        List<Map<String, Object>> hits = service.retrieve("连接池 耗尽", "assistant", 3);

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0)).containsEntry("problem", "数据库连接池耗尽");
    }

    @Test
    void feedbackRecomputesConfidenceAndDeprecates() {
        OpsExperience e = capture("磁盘写满", "清理日志", "disk");

        for (int i = 0; i < 5; i++) {
            service.feedback(e.getId(), false);
        }
        OpsExperience after = mapper.selectById(e.getId());

        assertThat(after.getFailCount()).isEqualTo(5);
        assertThat(after.getConfidence()).isLessThan(0.2);
        assertThat(after.getStatus()).isEqualTo("DEPRECATED");

        service.feedback(e.getId(), true);
        assertThat(mapper.selectById(e.getId()).getSuccessCount()).isEqualTo(1);
    }

    @Test
    void evolveDecaysStaleAndDeprecatesLowConfidence() {
        OpsExperience stale = capture("陈旧问题A", "旧方案", "a");
        stale.setConfidence(0.21);
        stale.setSuccessCount(3);
        stale.setFailCount(2);
        stale.setCreateTime(LocalDateTime.now().minusDays(60));
        stale.setLastUsedTime(null);
        mapper.updateById(stale);

        OpsExperience fresh = capture("新鲜问题B", "新方案", "b");
        fresh.setCreateTime(LocalDateTime.now());
        mapper.updateById(fresh);

        Map<String, Object> result = service.evolve();

        assertThat(result).containsEntry("scanned", 2).containsEntry("decayed", 1).containsEntry("deprecated", 1);
        assertThat(mapper.selectById(stale.getId()).getStatus()).isEqualTo("DEPRECATED");
        assertThat(mapper.selectById(fresh.getId()).getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void experienceBlocksFormatsTopExperiences() {
        capture("CPU 打满", "扩容副本", "cpu");
        List<String> blocks = service.experienceBlocks("assistant", 5);
        assertThat(blocks).hasSize(1);
        assertThat(blocks.get(0)).contains("问题: CPU 打满").contains("方案: 扩容副本").contains("置信度");
    }

    @Test
    void statsCountsByStatus() {
        capture("问题1", "方案1", null);
        OpsExperience deprecated = capture("问题2", "方案2", null);
        for (int i = 0; i < 5; i++) {
            service.feedback(deprecated.getId(), false);
        }

        Map<String, Object> stats = service.stats();

        assertThat(stats).containsEntry("total", 2).containsEntry("active", 1).containsEntry("deprecated", 1);
    }
}
