package com.hermes.agent.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.hermes.agent.entity.KnowledgeNode;
import com.hermes.agent.entity.ToolCall;
import com.hermes.agent.mapper.AgentProfileMapper;
import com.hermes.agent.mapper.AiChatMessageMapper;
import com.hermes.agent.mapper.AiChatSessionMapper;
import com.hermes.agent.mapper.AlertRecordMapper;
import com.hermes.agent.mapper.AlertReportMapper;
import com.hermes.agent.mapper.BizMetricMapper;
import com.hermes.agent.mapper.CrRepositoryMapper;
import com.hermes.agent.mapper.KnowledgeNodeMapper;
import com.hermes.agent.mapper.OpsGrantMapper;
import com.hermes.agent.mapper.SysProjectMapper;
import com.hermes.agent.mapper.SysUserMapper;
import com.hermes.agent.mapper.ToolCallMapper;
import com.hermes.agent.platform.PlatformLogBuffer;
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
 * 平台配置测试：平台日志过滤、用量聚合（工具调用/会话/消息）、数据看板计数。
 */
class PlatformConfigServiceTest {

    private SingleConnectionDataSource dataSource;
    private SqlSession sqlSession;
    private PlatformConfigService service;
    private PlatformLogBuffer logBuffer;
    private ToolCallMapper toolCallMapper;
    private KnowledgeNodeMapper kbMapper;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        new ResourceDatabasePopulator(new ClassPathResource("db/schema-sqlite.sql")).execute(dataSource);
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setLocalCacheScope(LocalCacheScope.STATEMENT);
        for (Class<?> mapper : List.of(ToolCallMapper.class, AiChatSessionMapper.class, AiChatMessageMapper.class,
                AgentProfileMapper.class, AlertRecordMapper.class, AlertReportMapper.class, BizMetricMapper.class,
                KnowledgeNodeMapper.class, OpsGrantMapper.class, CrRepositoryMapper.class, SysProjectMapper.class,
                SysUserMapper.class)) {
            configuration.addMapper(mapper);
        }
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(configuration);
        sqlSession = factory.getObject().openSession(true);

        toolCallMapper = sqlSession.getMapper(ToolCallMapper.class);
        kbMapper = sqlSession.getMapper(KnowledgeNodeMapper.class);
        logBuffer = new PlatformLogBuffer();
        service = new PlatformConfigService(logBuffer, toolCallMapper,
                sqlSession.getMapper(AiChatSessionMapper.class), sqlSession.getMapper(AiChatMessageMapper.class),
                sqlSession.getMapper(AgentProfileMapper.class), sqlSession.getMapper(AlertRecordMapper.class),
                sqlSession.getMapper(AlertReportMapper.class), sqlSession.getMapper(BizMetricMapper.class),
                kbMapper, sqlSession.getMapper(OpsGrantMapper.class), sqlSession.getMapper(CrRepositoryMapper.class),
                sqlSession.getMapper(SysProjectMapper.class), sqlSession.getMapper(SysUserMapper.class));
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

    private void call(String toolCode, String channel, int success, long durationMs, LocalDateTime when) {
        ToolCall c = new ToolCall();
        c.setTraceId("t-" + toolCode);
        c.setToolCode(toolCode);
        c.setChannel(channel);
        c.setSafetyLevel("READ");
        c.setSuccess(success);
        c.setDurationMs(durationMs);
        c.setCreateTime(when);
        toolCallMapper.insert(c);
    }

    @Test
    @SuppressWarnings("unchecked")
    void usageAggregatesToolCalls() {
        LocalDateTime now = LocalDateTime.now();
        call("log.search", "chat", 1, 100, now);
        call("log.search", "chat", 1, 200, now);
        call("alert.query", "flow", 0, 300, now);

        Map<String, Object> usage = service.usage(7);

        Map<String, Object> tc = (Map<String, Object>) usage.get("toolCalls");
        assertThat(tc).containsEntry("total", 3).containsEntry("success", 2).containsEntry("failure", 1);
        assertThat(tc).containsEntry("successRate", 66.7).containsEntry("avgDurationMs", 200L);
        assertThat((List<Map<String, Object>>) usage.get("byTool"))
                .extracting(r -> r.get("toolCode") + "=" + r.get("count"))
                .contains("log.search=2", "alert.query=1");
        assertThat((List<Map<String, Object>>) usage.get("byChannel"))
                .extracting(r -> r.get("channel") + "=" + r.get("count"))
                .contains("chat=2", "flow=1");
    }

    @Test
    @SuppressWarnings("unchecked")
    void usageWindowExcludesOldCalls() {
        call("log.search", "chat", 1, 10, LocalDateTime.now());
        call("log.search", "chat", 1, 10, LocalDateTime.now().minusDays(10));

        Map<String, Object> tc = (Map<String, Object>) service.usage(7).get("toolCalls");

        assertThat(tc).containsEntry("total", 1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void dashboardCountsByModule() {
        call("log.search", "chat", 1, 10, LocalDateTime.now());
        call("alert.query", "chat", 1, 10, LocalDateTime.now());
        KnowledgeNode doc = new KnowledgeNode();
        doc.setCode("kb-1");
        doc.setName("文档");
        doc.setNodeType("DOC");
        doc.setParentId(0L);
        kbMapper.insert(doc);

        Map<String, Object> counts = (Map<String, Object>) service.dashboard().get("counts");

        assertThat(counts).containsEntry("toolCalls", 2L).containsEntry("kbDocs", 1L);
        assertThat((List<?>) service.dashboard().get("recentToolCalls")).hasSize(2);
    }

    @Test
    void logsFilterByLevelAndKeyword() {
        long now = System.currentTimeMillis();
        logBuffer.record("INFO", "a.B", "main", "启动完成", now);
        logBuffer.record("ERROR", "a.B", "main", "数据库连接失败 timeout", now);
        logBuffer.record("ERROR", "a.C", "pool", "其他错误", now);

        assertThat(service.logs("ERROR", null, 10)).hasSize(2);
        assertThat(service.logs("ERROR", "timeout", 10)).hasSize(1);
        assertThat(service.logs(null, "启动", 10)).hasSize(1);
        // 时间倒序：最后写入的在最前
        assertThat(service.logs(null, null, 10).get(0)).containsEntry("message", "其他错误");
    }
}
