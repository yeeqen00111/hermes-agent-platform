package com.hermes.agent.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hermes.agent.entity.AgentProfile;
import com.hermes.agent.entity.AlertRecord;
import com.hermes.agent.entity.AlertReport;
import com.hermes.agent.llm.LlmGateway;
import com.hermes.agent.llm.LlmMessage;
import com.hermes.agent.mapper.AlertRecordMapper;
import com.hermes.agent.mapper.AlertReportMapper;
import com.hermes.agent.notify.NotificationGateway;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 【AI】智能告警报表测试：范围聚合、markdown 生成、发送与最近正文留存。
 */
class AlertReportServiceTest {

    private SingleConnectionDataSource dataSource;
    private SqlSession sqlSession;
    private AlertReportService service;
    private NotificationGateway gateway;
    private LlmGateway llmGateway;
    private AlertReportMapper reportMapper;
    private AlertRecordMapper recordMapper;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        new ResourceDatabasePopulator(new ClassPathResource("db/schema-sqlite.sql")).execute(dataSource);
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setLocalCacheScope(LocalCacheScope.STATEMENT);
        configuration.addMapper(AlertReportMapper.class);
        configuration.addMapper(AlertRecordMapper.class);
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(configuration);
        sqlSession = factory.getObject().openSession(true);

        reportMapper = sqlSession.getMapper(AlertReportMapper.class);
        recordMapper = sqlSession.getMapper(AlertRecordMapper.class);
        gateway = mock(NotificationGateway.class);
        llmGateway = mock(LlmGateway.class);
        when(llmGateway.chat(any(AgentProfile.class), anyList())).thenReturn("分析要点：核心接口错误偏高，建议排查依赖。");
        service = new AlertReportService(reportMapper, recordMapper, gateway, llmGateway, new ObjectMapper());
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

    private void record(String system, String level, String type, String content) {
        AlertRecord r = new AlertRecord();
        r.setRuleCode("rule-" + type);
        r.setProjectName(system);
        r.setSystemName(system);
        r.setLogLevel(level);
        r.setContent(content);
        r.setAlertType(type);
        r.setStatus("SENT");
        r.setLogTime(LocalDateTime.now());
        r.setCreateTime(LocalDateTime.now());
        recordMapper.insert(r);
    }

    private AlertReport config(String code, Integer frequencyHours, String scope, String channel) {
        AlertReport cfg = new AlertReport();
        cfg.setCode(code);
        cfg.setName(code);
        cfg.setFrequencyHours(frequencyHours);
        cfg.setScope(scope);
        cfg.setPrompt("请分析告警");
        cfg.setChannelCode(channel);
        cfg.setRecipient("ops@example.com");
        cfg.setEnabled(1);
        return service.saveReport(cfg);
    }

    @Test
    void previewAggregatesRecordsAndCallsLlm() {
        record("订单系统", "ERROR", "ALERT", "boom-1");
        record("订单系统", "ERROR", "PROMOTE", "boom-2");

        Map<String, Object> out = service.preview("请分析", Map.of("lookbackHours", 2));

        assertThat(out).containsEntry("success", true).containsEntry("recordCount", 2);
        String md = String.valueOf(out.get("markdown"));
        assertThat(md).contains("# 智能告警报表").contains("告警总数：2").contains("AI 分析")
                .contains("分析要点：核心接口错误偏高");
    }

    @Test
    void previewScopeFiltersByType() {
        record("订单系统", "ERROR", "ALERT", "boom-1");
        record("订单系统", "ERROR", "PROMOTE", "boom-2");

        Map<String, Object> out = service.preview("请分析", Map.of("alertType", "PROMOTE"));

        assertThat(out).containsEntry("recordCount", 1);
    }

    @Test
    void runSendsAndKeepsLastContent() {
        record("订单系统", "ERROR", "ALERT", "boom-1");
        config("hourly", 2, "{\"lookbackHours\":2}", "feishu-ops");
        when(gateway.sendByCode(eq("feishu-ops"), anyString(), anyString(), anyString())).thenReturn(true);

        Map<String, Object> out = service.run("hourly", Map.of());

        assertThat(out).containsEntry("success", true).containsEntry("sent", true).containsEntry("status", "SENT");
        AlertReport stored = reportMapper.selectOne(new LambdaQueryWrapper<AlertReport>()
                .eq(AlertReport::getCode, "hourly"));
        assertThat(stored.getLastContent()).contains("# 智能告警报表");
        assertThat(stored.getLastSendTime()).isNotNull();
    }

    @Test
    void runWithoutChannelIsNoChannelButStillGenerates() {
        record("订单系统", "ERROR", "ALERT", "boom-1");
        config("noc", 2, null, null);

        Map<String, Object> out = service.run("noc", Map.of());

        assertThat(out).containsEntry("status", "NO_CHANNEL").containsEntry("sent", false);
    }

    @Test
    void savePresetsFrequencyAndLastSendTime() {
        AlertReport cfg = config("c1", null, null, null);
        assertThat(cfg.getFrequencyHours()).isEqualTo(2);
        assertThat(cfg.getLastSendTime()).isNotNull();
    }

    @Test
    void unknownReportFails() {
        assertThat(service.run("nope", Map.of())).containsEntry("success", false);
    }
}
