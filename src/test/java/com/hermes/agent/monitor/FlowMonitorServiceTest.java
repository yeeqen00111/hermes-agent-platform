package com.hermes.agent.monitor;

import com.hermes.agent.entity.FlowStepLog;
import com.hermes.agent.mapper.FlowStepLogMapper;
import com.hermes.agent.notify.NotificationGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class FlowMonitorServiceTest {

    private FlowStepLogMapper stepMapper;
    private NotificationGateway gateway;
    private FlowMonitorService service;

    @BeforeEach
    void setUp() {
        stepMapper = mock(FlowStepLogMapper.class);
        gateway = mock(NotificationGateway.class);
        service = new FlowMonitorService(stepMapper, gateway);
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "slowStepMs", 1000L);
        ReflectionTestUtils.setField(service, "stuckStepMs", 1000L);
        ReflectionTestUtils.setField(service, "alertChannelType", "FEISHU");
        ReflectionTestUtils.setField(service, "alertRecipient", "ops@example.com");
        ReflectionTestUtils.setField(service, "alertCacheMax", 10000);
    }

    private FlowStepLog failedStep(long id) {
        FlowStepLog s = new FlowStepLog();
        s.setId(id);
        s.setRunId("run-1");
        s.setStepCode("s" + id);
        s.setStepName("步骤" + id);
        s.setStatus("FAILED");
        s.setEndTime(LocalDateTime.now());
        return s;
    }

    /** 一轮扫描固定 4 次 select：outcomes / stuck / slow / order；仅 outcomes 有数据 */
    private void stubScan(FlowStepLog... outcomes) {
        doReturn(List.of(outcomes), List.of(), List.of(), List.of())
                .when(stepMapper).selectList(any());
    }

    @Test
    void watermarkNotAdvancedWhenScanFails() {
        LocalDateTime before = (LocalDateTime) ReflectionTestUtils.getField(service, "lastScan");
        doThrow(new RuntimeException("db down")).when(stepMapper).selectList(any());

        service.scan();

        assertThat((LocalDateTime) ReflectionTestUtils.getField(service, "lastScan"))
                .as("扫描失败时 watermark 不得推进")
                .isEqualTo(before);

        stubScan();
        service.scan();
        assertThat((LocalDateTime) ReflectionTestUtils.getField(service, "lastScan"))
                .as("扫描成功后 watermark 前进")
                .isAfter(before);
    }

    @Test
    void sameFailedStepIsAlertedOnlyOnceAcrossScans() {
        FlowStepLog step = failedStep(1);

        stubScan(step);
        service.scan();
        stubScan(step);
        service.scan();

        verify(gateway, times(1))
                .sendByType(eq("FEISHU"), eq("ops@example.com"), anyString(), anyString());
    }

    @Test
    void alertCacheIsBoundedAndEvictedKeysCanAlertAgain() {
        ReflectionTestUtils.setField(service, "alertCacheMax", 1);
        FlowStepLog a = failedStep(1);
        FlowStepLog b = failedStep(2);

        stubScan(a);
        service.scan();
        stubScan(b);
        service.scan();
        stubScan(a);
        service.scan();

        // 上限 1：a 入缓存 → b 入并逐出 a → a 再次可告警，共 3 次
        verify(gateway, times(3))
                .sendByType(eq("FEISHU"), eq("ops@example.com"), anyString(), anyString());
    }
}
