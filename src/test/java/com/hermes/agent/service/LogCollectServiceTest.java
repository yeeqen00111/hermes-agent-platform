package com.hermes.agent.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.hermes.agent.entity.LogChannel;
import com.hermes.agent.entity.LogParseRule;
import com.hermes.agent.mapper.LogChannelMapper;
import com.hermes.agent.mapper.LogParseRuleMapper;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 日志采集（白板·系统管理）服务测试：跑真 SQLite，验证通道 CRUD、filebeat 示例、五级解析预览。
 */
class LogCollectServiceTest {

    private SingleConnectionDataSource dataSource;
    private SqlSession sqlSession;
    private LogCollectService service;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        new ResourceDatabasePopulator(new ClassPathResource("db/schema-sqlite.sql")).execute(dataSource);
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setLocalCacheScope(LocalCacheScope.STATEMENT);
        configuration.addMapper(LogChannelMapper.class);
        configuration.addMapper(LogParseRuleMapper.class);
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(configuration);
        sqlSession = factory.getObject().openSession(true);
        service = new LogCollectService(sqlSession.getMapper(LogChannelMapper.class),
                sqlSession.getMapper(LogParseRuleMapper.class));
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

    private void saveKafkaChannel() {
        LogChannel c = new LogChannel();
        c.setCode("kafka-app");
        c.setName("应用日志");
        c.setChannelType("KAFKA");
        c.setConfig("{\"brokers\":\"kafka:9092\",\"topic\":\"app-log\",\"path\":\"/var/log/app/*.log\",\"groupId\":\"hermes\"}");
        c.setEnabled(1);
        service.saveChannel(c);
    }

    @Test
    void saveAndListChannels() {
        saveKafkaChannel();
        assertThat(service.listChannels()).extracting(LogChannel::getCode).containsExactly("kafka-app");
    }

    @Test
    void filebeatExampleUsesConfiguredBrokerTopicAndPath() {
        saveKafkaChannel();
        Map<String, Object> ex = service.filebeatExample("kafka-app");
        assertThat(ex).containsEntry("success", true);
        assertThat((String) ex.get("content"))
                .contains("kafka:9092").contains("app-log").contains("/var/log/app/*.log").contains("ndjson");
    }

    @Test
    void filebeatExampleForUnknownChannelFails() {
        assertThat(service.filebeatExample("nope")).containsEntry("success", false);
    }

    @Test
    void previewMapsFiveLevelsAndLevelAlias() {
        LogParseRule r = new LogParseRule();
        r.setSystemField("app");
        r.setTimeField("@timestamp");
        r.setLevelField("level");
        r.setLevelMapping("{\"WARN\":\"WARNING\"}");
        r.setContentField("message");
        r.setServiceField("service");
        r.setSampleJson("{\"app\":\"order\",\"@timestamp\":\"2026-10-08 10:00:00.123\","
                + "\"level\":\"WARN\",\"message\":\"slow\",\"service\":\"order-api\"}");
        assertThat(service.preview(r))
                .containsEntry("success", true)
                .containsEntry("system", "order")
                .containsEntry("time", "2026-10-08 10:00:00.123")
                .containsEntry("level", "WARNING")
                .containsEntry("content", "slow")
                .containsEntry("service", "order-api");
    }

    @Test
    void previewRejectsInvalidJson() {
        LogParseRule r = new LogParseRule();
        r.setSampleJson("not-json");
        assertThat(service.preview(r)).containsEntry("success", false);
    }

    @Test
    void previewRejectsEmptySample() {
        assertThat(service.preview(new LogParseRule())).containsEntry("success", false);
    }
}
