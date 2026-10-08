package com.hermes.agent.persona;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.hermes.agent.entity.AgentProfile;
import com.hermes.agent.mapper.AgentProfileMapper;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 灰度版本绑定（ADR-009 / agent-platform §8.2）：新会话按比例指向灰度版本。
 */
class SessionVersionResolverTest {

    private SingleConnectionDataSource dataSource;
    private SqlSession sqlSession;
    private AgentProfileMapper profileMapper;
    private SessionVersionResolver resolver;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        new ResourceDatabasePopulator(new ClassPathResource("db/schema-sqlite.sql")).execute(dataSource);
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setLocalCacheScope(LocalCacheScope.STATEMENT);
        configuration.addMapper(AgentProfileMapper.class);
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(configuration);
        sqlSession = factory.getObject().openSession(true);
        profileMapper = sqlSession.getMapper(AgentProfileMapper.class);
        resolver = new SessionVersionResolver(profileMapper);
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

    private AgentProfile assistant() {
        return profileMapper.selectList(null).stream()
                .filter(p -> "assistant".equals(p.getAgentCode()))
                .findFirst().orElseThrow();
    }

    @Test
    void pickReturnsPublishedVersionWhenGrayOff() {
        assertThat(SessionVersionResolver.pick(5, null, null, 0)).isEqualTo(5);
        assertThat(SessionVersionResolver.pick(5, 2, 0, 0)).isEqualTo(5);
        assertThat(SessionVersionResolver.pick(5, 2, -1, 0)).isEqualTo(5);
    }

    @Test
    void pickHonorsRatioBoundary() {
        // ratio=30 → roll ∈ [0,30) 命中灰度
        assertThat(SessionVersionResolver.pick(5, 2, 30, 0)).isEqualTo(2);
        assertThat(SessionVersionResolver.pick(5, 2, 30, 29)).isEqualTo(2);
        assertThat(SessionVersionResolver.pick(5, 2, 30, 30)).isEqualTo(5);
        // ratio=100 → 恒灰度；>100 收敛到 100
        assertThat(SessionVersionResolver.pick(5, 2, 100, 99)).isEqualTo(2);
        assertThat(SessionVersionResolver.pick(5, 2, 150, 99)).isEqualTo(2);
    }

    @Test
    void resolveReadsProfileGrayConfig() {
        AgentProfile p = assistant();
        p.setCurrentVersion(5);
        p.setGrayVersion(2);
        p.setGrayRatio(100);
        profileMapper.updateById(p);

        assertThat(resolver.resolve("assistant")).isEqualTo(2);

        p.setGrayRatio(0);
        profileMapper.updateById(p);
        assertThat(resolver.resolve("assistant")).isEqualTo(5);

        assertThat(resolver.resolve("no-such-agent")).isNull();
        assertThat(resolver.resolve(null)).isNull();
    }
}
