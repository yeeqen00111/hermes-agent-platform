package com.hermes.agent.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.hermes.agent.entity.AlertRecord;
import com.hermes.agent.entity.AlertRule;
import com.hermes.agent.entity.LogIngestStat;
import com.hermes.agent.entity.LogParseRule;
import com.hermes.agent.mapper.AlertRecordMapper;
import com.hermes.agent.mapper.AlertRuleMapper;
import com.hermes.agent.mapper.LogIngestStatMapper;
import com.hermes.agent.mapper.LogParseRuleMapper;
import com.hermes.agent.notify.NotificationGateway;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 日志分级-固化引擎测试：跑真 SQLite，验证 告警规则 / 白名单 / 提级 的优先级判定、
 * 级别过滤、通知去向与统计落库。
 */
class LogGradeServiceTest {

    private static final String RAW = """
            {"app":"order","ts":"2026-10-08 12:00:00.000","level":"ERROR","msg":"OutOfMemory on order-api","service":"order-api"}
            """;

    private SingleConnectionDataSource dataSource;
    private SqlSession sqlSession;
    private LogGradeService service;
    private NotificationGateway gateway;
    private AlertRecordMapper recordMapper;
    private AlertRuleMapper ruleMapper;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        new ResourceDatabasePopulator(new ClassPathResource("db/schema-sqlite.sql")).execute(dataSource);
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setLocalCacheScope(LocalCacheScope.STATEMENT);
        configuration.addMapper(LogParseRuleMapper.class);
        configuration.addMapper(AlertRuleMapper.class);
        configuration.addMapper(AlertRecordMapper.class);
        configuration.addMapper(LogIngestStatMapper.class);
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(configuration);
        sqlSession = factory.getObject().openSession(true);

        LogParseRuleMapper parseMapper = sqlSession.getMapper(LogParseRuleMapper.class);
        ruleMapper = sqlSession.getMapper(AlertRuleMapper.class);
        recordMapper = sqlSession.getMapper(AlertRecordMapper.class);
        LogIngestStatMapper statMapper = sqlSession.getMapper(LogIngestStatMapper.class);

        gateway = mock(NotificationGateway.class);
        service = new LogGradeService(new LogCollectService(null, parseMapper), parseMapper, ruleMapper,
                recordMapper, statMapper, gateway);

        LogParseRule parse = new LogParseRule();
        parse.setCode("default");
        parse.setName("默认解析");
        parse.setSystemField("app");
        parse.setTimeField("ts");
        parse.setTimeFormat("yyyy-MM-dd HH:mm:ss.SSS");
        parse.setLevelField("level");
        parse.setContentField("msg");
        parse.setServiceField("service");
        parse.setEnabled(1);
        parseMapper.insert(parse);
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

    private void saveRule(String code, String type, String pattern, String levelFilter, String channel) {
        AlertRule r = new AlertRule();
        r.setCode(code);
        r.setName(code);
        r.setRuleType(type);
        r.setMatchPattern(pattern);
        r.setLevelFilter(levelFilter);
        r.setChannelCode(channel);
        r.setRecipient("ops@example.com");
        r.setEnabled(1);
        ruleMapper.insert(r);
    }

    @Test
    void whitelistSuppressesAndSkipsNotify() {
        saveRule("alert-all", "ALERT", null, null, "feishu-ops");
        saveRule("wl-oom", "WHITELIST", "OutOfMemory", null, null);

        Map<String, Object> out = service.ingest(RAW, null);

        assertThat(out).containsEntry("decision", "SUPPRESSED");
        verify(gateway, never()).sendByCode(anyString(), anyString(), anyString(), anyString());
        List<AlertRecord> records = recordMapper.selectList(new LambdaQueryWrapper<>());
        assertThat(records).hasSize(1);
        assertThat(records.get(0).getStatus()).isEqualTo("SUPPRESSED");
        assertThat(stat().getTotalSuppress()).isEqualTo(1);
        assertThat(stat().getTotalAlert()).isEqualTo(0);
    }

    @Test
    void promoteWinsOverAlert() {
        saveRule("alert-all", "ALERT", null, null, "feishu-ops");
        saveRule("promote-critical", "PROMOTE", "OutOfMemory", "ERROR", "feishu-ops");
        when(gateway.sendByCode(eq("feishu-ops"), anyString(), anyString(), anyString())).thenReturn(true);

        Map<String, Object> out = service.ingest(RAW, null);

        assertThat(out).containsEntry("decision", "PROMOTE").containsEntry("status", "SENT");
        assertThat(recordMapper.selectList(new LambdaQueryWrapper<>()).get(0).getAlertType()).isEqualTo("PROMOTE");
        assertThat(stat().getTotalPromote()).isEqualTo(1);
        assertThat(stat().getLastIngestTime()).isNotNull();
    }

    @Test
    void alertRuleSendsToChannel() {
        saveRule("alert-all", "ALERT", "OutOfMemory", null, "feishu-ops");
        when(gateway.sendByCode(eq("feishu-ops"), anyString(), anyString(), anyString())).thenReturn(true);

        Map<String, Object> out = service.ingest(RAW, null);

        assertThat(out).containsEntry("decision", "ALERT").containsEntry("notified", true);
        verify(gateway).sendByCode(anyString(), anyString(), anyString(), anyString());
        assertThat(stat().getTotalAlert()).isEqualTo(1);
    }

    @Test
    void alertWithoutChannelIsNoChannel() {
        saveRule("alert-all", "ALERT", null, null, null);

        Map<String, Object> out = service.ingest(RAW, null);

        assertThat(out).containsEntry("status", "NO_CHANNEL");
        assertThat(recordMapper.selectList(new LambdaQueryWrapper<>()).get(0).getStatus()).isEqualTo("NO_CHANNEL");
    }

    @Test
    void levelFilterSkipsNonMatchingLogs() {
        saveRule("alert-error-only", "ALERT", null, "FATAL", "feishu-ops");

        Map<String, Object> out = service.ingest(RAW, null);

        assertThat(out).containsEntry("decision", "IGNORED");
        assertThat(recordMapper.selectList(new LambdaQueryWrapper<>())).isEmpty();
        verify(gateway, never()).sendByCode(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void projectMismatchSkipsRule() {
        AlertRule r = new AlertRule();
        r.setCode("alert-other-project");
        r.setName("其它项目");
        r.setRuleType("ALERT");
        r.setProjectName("other-system");
        r.setChannelCode("feishu-ops");
        r.setEnabled(1);
        ruleMapper.insert(r);

        assertThat(service.ingest(RAW, null)).containsEntry("decision", "IGNORED");
    }

    private LogIngestStat stat() {
        return sqlSession.getMapper(LogIngestStatMapper.class)
                .selectOne(new LambdaQueryWrapper<LogIngestStat>().eq(LogIngestStat::getProjectName, "order"));
    }
}
