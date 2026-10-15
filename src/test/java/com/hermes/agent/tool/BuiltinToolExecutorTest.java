package com.hermes.agent.tool;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.hermes.agent.dto.ToolRequest;
import com.hermes.agent.dto.ToolResponse;
import com.hermes.agent.entity.AlertHandle;
import com.hermes.agent.entity.AlertRecord;
import com.hermes.agent.entity.BizMetric;
import com.hermes.agent.entity.BizMetricSample;
import com.hermes.agent.mapper.AlertHandleMapper;
import com.hermes.agent.mapper.AlertRecordMapper;
import com.hermes.agent.mapper.BizMetricMapper;
import com.hermes.agent.mapper.BizMetricSampleMapper;
import com.hermes.agent.mapper.KnowledgeNodeMapper;
import com.hermes.agent.mapper.NacosServerMapper;
import com.hermes.agent.service.KnowledgeService;
import com.hermes.agent.service.NacosDataPlaneService;
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
 * 内置工具执行器回归（隔离 SQLite）：log.search / log.aggregate / log.context /
 * alert.query / alert.acknowledge / alert.resolve / alert.suppress /
 * knowledge.search / database.metric.query / report.generate 全部走真实本地数据面。
 */
class BuiltinToolExecutorTest {

    private SingleConnectionDataSource dataSource;
    private SqlSession sqlSession;
    private BuiltinToolExecutor executor;
    private AlertRecordMapper recordMapper;
    private BizMetricMapper metricMapper;
    private BizMetricSampleMapper sampleMapper;
    private KnowledgeService knowledgeService;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        new ResourceDatabasePopulator(new ClassPathResource("db/schema-sqlite.sql")).execute(dataSource);
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setLocalCacheScope(LocalCacheScope.STATEMENT);
        configuration.addMapper(AlertRecordMapper.class);
        configuration.addMapper(AlertHandleMapper.class);
        configuration.addMapper(BizMetricMapper.class);
        configuration.addMapper(BizMetricSampleMapper.class);
        configuration.addMapper(KnowledgeNodeMapper.class);
        configuration.addMapper(NacosServerMapper.class);
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(configuration);
        sqlSession = factory.getObject().openSession(true);

        recordMapper = sqlSession.getMapper(AlertRecordMapper.class);
        metricMapper = sqlSession.getMapper(BizMetricMapper.class);
        sampleMapper = sqlSession.getMapper(BizMetricSampleMapper.class);
        knowledgeService = new KnowledgeService(
                sqlSession.getMapper(KnowledgeNodeMapper.class));
        NacosDataPlaneService nacosDataPlaneService = new NacosDataPlaneService(
                sqlSession.getMapper(NacosServerMapper.class));
        executor = new BuiltinToolExecutor(recordMapper,
                sqlSession.getMapper(AlertHandleMapper.class), knowledgeService,
                metricMapper, sampleMapper, nacosDataPlaneService);
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

    private AlertRecord record(String system, String server, String level, String content,
                               LocalDateTime time, String alertType) {
        AlertRecord r = new AlertRecord();
        r.setRuleCode("rule-1");
        r.setProjectName(system);
        r.setSystemName(system);
        r.setServerName(server);
        r.setLogLevel(level);
        r.setContent(content);
        r.setLogTime(time);
        r.setAlertType(alertType);
        r.setStatus("SENT");
        r.setCreateTime(LocalDateTime.now());
        recordMapper.insert(r);
        return r;
    }

    private ToolResponse run(String toolCode, Map<String, Object> args) {
        ToolRequest request = new ToolRequest();
        request.setArguments(args);
        request.setUserId(9L);
        return executor.execute(toolCode, request, System.currentTimeMillis());
    }

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 15, 9, 0, 0);

    @Test
    void supportsRegisteredTools() {
        assertThat(executor.supports("log.search")).isTrue();
        assertThat(executor.supports("alert.suppress")).isTrue();
        assertThat(executor.supports("skill.load")).isFalse();
        assertThat(executor.supports("unknown.tool")).isFalse();
    }

    @Test
    void logSearchFiltersByServiceLevelKeywordAndWindow() {
        record("order", "order-api", "ERROR", "db timeout", T0, "ALERT");
        record("order", "order-web", "WARN", "slow query", T0.plusMinutes(1), "ALERT");
        record("pay", "pay-api", "ERROR", "connect refused", T0.plusMinutes(2), "ALERT");

        ToolResponse ok = run("log.search", Map.of(
                "service", "order", "from", "2026-10-15 08:00:00", "to", "2026-10-15 10:00:00",
                "level", "error", "keyword", "db"));
        assertThat(ok.getSuccess()).isTrue();
        Map<?, ?> data = (Map<?, ?>) ok.getData();
        assertThat(data.get("total")).isEqualTo(1);
        List<?> rows = (List<?>) data.get("records");
        assertThat(rows).hasSize(1);
        Map<?, ?> row = (Map<?, ?>) rows.get(0);
        assertThat(row.get("level")).isEqualTo("ERROR");
        assertThat(row.get("content")).isEqualTo("db timeout");
    }

    @Test
    void logSearchMissingRequiredTimeIsInvalidArgument() {
        ToolResponse resp = run("log.search", Map.of("service", "order"));
        assertThat(resp.getSuccess()).isFalse();
        assertThat(resp.getErrorCode()).isEqualTo("INVALID_ARGUMENT");
    }

    @Test
    void logAggregateBucketsByLevelAndRejectsUnsupportedDimension() {
        record("order", "order-api", "ERROR", "a", T0, "ALERT");
        record("order", "order-api", "ERROR", "b", T0.plusMinutes(1), "ALERT");
        record("order", "order-api", "WARN", "c", T0.plusMinutes(2), "PROMOTE");

        Map<?, ?> data = (Map<?, ?>) run("log.aggregate", Map.of(
                "service", "order-api", "from", "2026-10-15T08:00:00", "to", "2026-10-15T10:00:00"))
                .getData();
        assertThat(data.get("scanned")).isEqualTo(3);
        List<?> buckets = (List<?>) data.get("buckets");
        assertThat(buckets).hasSize(2);

        ToolResponse bad = run("log.aggregate", Map.of(
                "service", "order-api", "from", "2026-10-15T08:00:00", "to", "2026-10-15T10:00:00",
                "groupBy", "exceptionType"));
        assertThat(bad.getSuccess()).isFalse();
        assertThat(bad.getErrorCode()).isEqualTo("INVALID_ARGUMENT");
    }

    @Test
    void logContextReturnsNeighborsOfSameServer() {
        AlertRecord first = record("order", "order-api", "INFO", "start", T0, "ALERT");
        AlertRecord mid = record("order", "order-api", "ERROR", "boom", T0.plusSeconds(1), "ALERT");
        AlertRecord last = record("order", "order-api", "INFO", "end", T0.plusSeconds(2), "ALERT");

        Map<?, ?> data = (Map<?, ?>) run("log.context",
                Map.of("eventId", String.valueOf(mid.getId()))).getData();
        Map<?, ?> target = (Map<?, ?>) data.get("target");
        assertThat(target.get("content")).isEqualTo("boom");
        assertThat(((List<?>) data.get("before"))).hasSize(1);
        assertThat(((List<?>) data.get("after"))).hasSize(1);

        ToolResponse missing = run("log.context", Map.of("eventId", "99999"));
        assertThat(missing.getErrorCode()).isEqualTo("EVENT_NOT_FOUND");
        // 保持引用不触发未使用告警
        assertThat(last.getId()).isNotNull();
        assertThat(first.getId()).isNotNull();
    }

    @Test
    void alertQueryMapsSeverityAndComputesHandleStatus() {
        AlertRecord p0 = record("order", "order-api", "ERROR", "down", T0, "ALERT");
        record("order", "order-api", "WARN", "degraded", T0.plusMinutes(1), "ALERT");

        AlertHandle handle = new AlertHandle();
        handle.setRecordId(p0.getId());
        handle.setAction("CONFIRMED");
        handle.setHandleBy("9");
        handle.setCreateTime(LocalDateTime.now());
        sqlSession.getMapper(AlertHandleMapper.class).insert(handle);

        Map<?, ?> data = (Map<?, ?>) run("alert.query", Map.of("severity", "P0")).getData();
        List<?> rows = (List<?>) data.get("records");
        assertThat(rows).hasSize(1);
        assertThat(((Map<?, ?>) rows.get(0)).get("status")).isEqualTo("CONFIRMED");

        Map<?, ?> all = (Map<?, ?>) run("alert.query", Map.of("status", "NEW")).getData();
        assertThat((List<?>) all.get("records")).hasSize(1);
        assertThat(((Map<?, ?>) ((List<?>) all.get("records")).get(0)).get("level")).isEqualTo("WARN");
    }

    @Test
    void alertHandleWritesAppendOnlyFlowAndRejectsUnknownAlert() {
        AlertRecord r = record("order", "order-api", "ERROR", "down", T0, "ALERT");

        ToolResponse ack = run("alert.acknowledge", Map.of("alertId", String.valueOf(r.getId()),
                "comment", "收到，处理中"));
        assertThat(ack.getSuccess()).isTrue();
        ToolResponse resolve = run("alert.resolve", Map.of("alertId", String.valueOf(r.getId())));
        assertThat(resolve.getSuccess()).isTrue();
        ToolResponse suppress = run("alert.suppress", Map.of("alertId", String.valueOf(r.getId())));
        assertThat(suppress.getSuccess()).isTrue();

        List<AlertHandle> handles = sqlSession.getMapper(AlertHandleMapper.class).selectList(null);
        assertThat(handles).extracting(AlertHandle::getAction)
                .containsExactly("CONFIRMED", "RESOLVED", "SUPPRESSED");
        assertThat(handles.get(0).getHandleBy()).isEqualTo("9");

        ToolResponse missing = run("alert.resolve", Map.of("alertId", "424242"));
        assertThat(missing.getErrorCode()).isEqualTo("ALERT_NOT_FOUND");
    }

    @Test
    void knowledgeSearchSlicesTopKAndCites() {
        com.hermes.agent.entity.KnowledgeNode doc = new com.hermes.agent.entity.KnowledgeNode();
        doc.setName("数据库应急预案");
        doc.setTitle("数据库应急预案");
        doc.setContent("当数据库连接超时时应先检查连接池配置");
        doc.setNodeType("DOC");
        knowledgeService.saveNode(doc);

        ToolResponse resp = run("knowledge.search", Map.of("query", "连接超时", "topK", "3"));
        assertThat(resp.getSuccess()).isTrue();
        Map<?, ?> data = (Map<?, ?>) resp.getData();
        assertThat(data.get("total")).isEqualTo(1);
        assertThat(resp.getCitations()).hasSize(1);
        assertThat(resp.getCitations().get(0).getKind()).isEqualTo("knowledge");
    }

    @Test
    void metricQueryReturnsDefinitionAndSamples() {
        BizMetric metric = new BizMetric();
        metric.setCode("order.qps");
        metric.setName("订单QPS");
        metric.setSystemName("order");
        metric.setMetricType("GAUGE");
        metric.setUnit("req/s");
        metric.setThresholdOp(">");
        metric.setThresholdValue(100.0);
        metric.setEnabled(1);
        metricMapper.insert(metric);

        BizMetricSample s1 = new BizMetricSample();
        s1.setMetricCode("order.qps");
        s1.setMetricValue(120.0);
        s1.setBreached(1);
        s1.setSampleTime(T0);
        sampleMapper.insert(s1);

        Map<?, ?> data = (Map<?, ?>) run("database.metric.query", Map.of(
                "metric", "order.qps", "from", "2026-10-15T08:00:00", "to", "2026-10-15T10:00:00"))
                .getData();
        assertThat(data.get("name")).isEqualTo("订单QPS");
        assertThat(data.get("total")).isEqualTo(1);
        assertThat(data.get("breachedCount")).isEqualTo(1L);

        ToolResponse missing = run("database.metric.query", Map.of(
                "metric", "no.such", "from", "2026-10-15T08:00:00", "to", "2026-10-15T10:00:00"));
        assertThat(missing.getErrorCode()).isEqualTo("METRIC_NOT_FOUND");
    }

    @Test
    void reportGenerateBuildsMarkdownAndRejectsOtherFormats() {
        record("order", "order-api", "ERROR", "down", T0, "ALERT");
        record("order", "order-api", "WARN", "slow", T0.plusMinutes(1), "PROMOTE");

        ToolResponse resp = run("report.generate", Map.of(
                "from", "2026-10-15T08:00:00", "to", "2026-10-15T10:00:00"));
        assertThat(resp.getSuccess()).isTrue();
        String report = String.valueOf(((Map<?, ?>) resp.getData()).get("report"));
        assertThat(report).contains("# 系统健康报表");
        assertThat(report).contains("固化记录 2 条");
        assertThat(report).contains("ALERT 1 条");

        ToolResponse bad = run("report.generate", Map.of(
                "from", "2026-10-15T08:00:00", "to", "2026-10-15T10:00:00", "format", "xlsx"));
        assertThat(bad.getErrorCode()).isEqualTo("FORMAT_UNSUPPORTED");
    }

    @Test
    void nacosToolsFailExplicitlyWhenNoServerConfigured() {
        ToolResponse resp = run("nacos.config.query", Map.of(
                "namespace", "public", "group", "DEFAULT_GROUP", "dataId", "app.yaml"));
        assertThat(resp.getSuccess()).isFalse();
        assertThat(resp.getErrorCode()).isEqualTo("NACOS_NOT_CONFIGURED");
    }

    private void knowledgeServiceSave(com.hermes.agent.entity.KnowledgeNode node) {
        // 直接经同一 SqlSession 落库，避免构造第二个服务实例
        sqlSession.getMapper(com.hermes.agent.mapper.KnowledgeNodeMapper.class).insert(node);
    }
}
