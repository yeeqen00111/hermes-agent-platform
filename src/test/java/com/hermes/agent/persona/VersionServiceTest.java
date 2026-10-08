package com.hermes.agent.persona;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hermes.agent.entity.AgentProfile;
import com.hermes.agent.entity.AgentVersion;
import com.hermes.agent.mapper.AgentContextFileMapper;
import com.hermes.agent.mapper.AgentProfileMapper;
import com.hermes.agent.mapper.AgentVersionMapper;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import static org.assertj.core.api.Assertions.assertThat;

class VersionServiceTest {

    private SingleConnectionDataSource dataSource;
    private SqlSession sqlSession;
    private VersionService versionService;
    private AgentProfileMapper profileMapper;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        new ResourceDatabasePopulator(new ClassPathResource("db/schema-sqlite.sql")).execute(dataSource);
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setLocalCacheScope(LocalCacheScope.STATEMENT);
        configuration.addMapper(AgentProfileMapper.class);
        configuration.addMapper(AgentContextFileMapper.class);
        configuration.addMapper(AgentVersionMapper.class);
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(configuration);
        sqlSession = factory.getObject().openSession(true);
        profileMapper = sqlSession.getMapper(AgentProfileMapper.class);
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        versionService = new VersionService(profileMapper,
                sqlSession.getMapper(AgentContextFileMapper.class),
                sqlSession.getMapper(AgentVersionMapper.class), objectMapper);
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
    void publishWritesImmutableSnapshotAndMovesPointer() {
        AgentVersion v1 = versionService.publish("assistant", "tester");

        assertThat(v1.getVersion()).isEqualTo(1);
        assertThat(v1.getSnapshot()).contains("assistant");
        assertThat(v1.getPublishBy()).isEqualTo("tester");

        AgentProfile p = assistant();
        assertThat(p.getCurrentVersion()).isEqualTo(1);
        assertThat(p.getStatus()).isEqualTo("PUBLISHED");

        AgentVersion v2 = versionService.publish("assistant", "tester");
        assertThat(v2.getVersion()).isEqualTo(2);
        assertThat(versionService.versions("assistant")).hasSize(2);
    }

    @Test
    void rollbackRestoresSnapshotContentAndPointer() {
        versionService.publish("assistant", "tester");

        AgentProfile p = assistant();
        p.setModelName("changed-model");
        profileMapper.updateById(p);
        versionService.publish("assistant", "tester");

        AgentProfile rolled = versionService.rollback("assistant", 1);

        assertThat(rolled.getModelName()).isEqualTo("deepseek-chat");
        assertThat(rolled.getCurrentVersion()).isEqualTo(1);
        assertThat(assistant().getModelName()).isEqualTo("deepseek-chat");
    }

    @Test
    void setGrayPointsAtPublishedVersionAndClears() {
        versionService.publish("assistant", "tester");   // v1

        AgentProfile grayed = versionService.setGray("assistant", 1, 30);
        assertThat(grayed.getGrayVersion()).isEqualTo(1);
        assertThat(grayed.getGrayRatio()).isEqualTo(30);

        AgentProfile cleared = versionService.setGray("assistant", null, 0);
        assertThat(cleared.getGrayVersion()).isNull();
        assertThat(cleared.getGrayRatio()).isZero();
        assertThat(assistant().getGrayVersion()).isNull();
    }

    @Test
    void setGrayRejectsUnknownVersion() {
        versionService.publish("assistant", "tester");

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> versionService.setGray("assistant", 99, 50))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("版本不存在");
    }

    @Test
    void snapshotExposesHistoricalPersona() {
        versionService.publish("assistant", "tester");   // v1 keeps deepseek-chat
        AgentProfile p = assistant();
        p.setModelName("new-model");
        profileMapper.updateById(p);
        versionService.publish("assistant", "tester");   // v2 keeps new-model

        VersionService.Snapshot v1 = versionService.snapshot("assistant", 1);
        assertThat(v1).isNotNull();
        assertThat(v1.profile().getModelName()).isEqualTo("deepseek-chat");

        assertThat(versionService.snapshot("assistant", 99)).isNull();
    }
}
