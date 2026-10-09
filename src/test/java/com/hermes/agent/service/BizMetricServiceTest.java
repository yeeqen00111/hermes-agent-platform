package com.hermes.agent.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.hermes.agent.entity.AlertRecord;
import com.hermes.agent.entity.BizMetric;
import com.hermes.agent.mapper.AlertRecordMapper;
import com.hermes.agent.mapper.BizMetricMapper;
import com.hermes.agent.mapper.BizMetricSampleMapper;
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
 * 业务指标监控测试：阈值判定、命中落告警记录 + 通知、监控视图最新值。
 */
class BizMetricServiceTest {

    private SingleConnectionDataSource dataSource;
    private SqlSession sqlSession;
    private BizMetricService service;
    private NotificationGateway gateway;
    private AlertRecordMapper recordMapper;
    private BizMetricMapper metricMapper;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        new ResourceDatabasePopulator(new ClassPathResource("db/schema-sqlite.sql")).execute(dataSource);
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setLocalCacheScope(LocalCacheScope.STATEMENT);
        configuration.addMapper(BizMetricMapper.class);
        configuration.addMapper(BizMetricSampleMapper.class);
        configuration.addMapper(AlertRecordMapper.class);
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(configuration);
        sqlSession = factory.getObject().openSession(true);

        metricMapper = sqlSession.getMapper(BizMetricMapper.class);
        recordMapper = sqlSession.getMapper(AlertRecordMapper.class);
        gateway = mock(NotificationGateway.class);
        service = new BizMetricService(metricMapper, sqlSession.getMapper(BizMetricSampleMapper.class),
                recordMapper, gateway);
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

    private void metric(String code, String op, Double threshold, String channel) {
        BizMetric m = new BizMetric();
        m.setCode(code);
        m.setName(code);
        m.setMetricPoint("point." + code);
        m.setSystemName("订单系统");
        m.setMetricType("耗时");
        m.setUnit("ms");
        m.setThresholdOp(op);
        m.setThresholdValue(threshold);
        m.setChannelCode(channel);
        m.setRecipient("ops@example.com");
        m.setEnabled(1);
        service.saveMetric(m);
    }

    @Test
    void breachWritesRecordAndNotifies() {
        metric("order-rt", ">", 100.0, "feishu-ops");
        when(gateway.sendByCode(eq("feishu-ops"), anyString(), anyString(), anyString())).thenReturn(true);

        Map<String, Object> out = service.ingest("order-rt", 150.0, null);

        assertThat(out).containsEntry("breached", true).containsEntry("status", "SENT");
        List<AlertRecord> records = recordMapper.selectList(new LambdaQueryWrapper<>());
        assertThat(records).hasSize(1);
        assertThat(records.get(0).getAlertType()).isEqualTo("METRIC");
        assertThat(records.get(0).getSystemName()).isEqualTo("订单系统");
        verify(gateway).sendByCode(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void belowThresholdNoRecordNorNotify() {
        metric("order-rt", ">", 100.0, "feishu-ops");

        Map<String, Object> out = service.ingest("order-rt", 50.0, null);

        assertThat(out).containsEntry("breached", false);
        assertThat(recordMapper.selectList(new LambdaQueryWrapper<>())).isEmpty();
        verify(gateway, never()).sendByCode(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void withoutThresholdNeverBreaches() {
        metric("order-rt", null, null, null);

        assertThat(service.ingest("order-rt", 9999.0, null)).containsEntry("breached", false);
    }

    @Test
    void lessThanOperatorWorks() {
        metric("stock", "<", 10.0, null);

        assertThat(service.ingest("stock", 5.0, null)).containsEntry("breached", true);
        assertThat(service.ingest("stock", 20.0, null)).containsEntry("breached", false);
    }

    @Test
    void unknownMetricFails() {
        assertThat(service.ingest("nope", 1.0, null)).containsEntry("success", false);
    }

    @Test
    void monitorReturnsLatestValue() {
        metric("order-rt", ">", 100.0, "feishu-ops");
        when(gateway.sendByCode(anyString(), anyString(), anyString(), anyString())).thenReturn(false);
        service.ingest("order-rt", 200.0, null);
        service.ingest("order-rt", 30.0, null);

        List<Map<String, Object>> rows = service.monitor();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("code", "order-rt")
                .containsEntry("latestValue", 30.0)
                .containsEntry("breached", false);
    }
}
