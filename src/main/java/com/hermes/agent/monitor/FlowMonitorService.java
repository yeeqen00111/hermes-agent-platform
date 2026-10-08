package com.hermes.agent.monitor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hermes.agent.entity.FlowStepLog;
import com.hermes.agent.mapper.FlowStepLogMapper;
import com.hermes.agent.notify.NotificationGateway;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 流程监控告警（白板：看板监控 → 状态告警"失败?"、告警 → 时间告警/时间先后）。
 * 定时扫 ai_flow_step_log，三类判定：
 *  1. 状态告警——步骤 FAILED
 *  2. 时间告警——单步耗时超阈值，或 RUNNING 卡死超阈值
 *  3. 时间先后——下游开始早于上游结束（时序倒挂）
 * 告警经平台通知网关发送（ADR-011：通道权威在 ◆ 控制塔，本地通道为回退）。
 * 去重为进程内存（重启后 watermark 重置为当前时刻，不重告历史）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowMonitorService {

    private final FlowStepLogMapper stepMapper;
    private final NotificationGateway gateway;

    @Value("${hermes.monitor.enabled:true}")
    private boolean enabled;

    @Value("${hermes.monitor.slow-step-ms:300000}")
    private long slowStepMs;

    @Value("${hermes.monitor.stuck-step-ms:600000}")
    private long stuckStepMs;

    @Value("${hermes.monitor.alert-channel-type:FEISHU}")
    private String alertChannelType;

    @Value("${hermes.monitor.alert-recipient:}")
    private String alertRecipient;

    @Value("${hermes.monitor.alert-cache-max:10000}")
    private int alertCacheMax = 10000;

    /** 启动时刻作为首个 watermark：不回告历史。仅在整轮扫描成功后推进，失败时下轮重扫（防漏报）。 */
    private volatile LocalDateTime lastScan = LocalDateTime.now();

    /**
     * 已告警的步骤（key = 类型:stepLogId），防同一步骤反复告警。
     * 有界 FIFO：超过 alertCacheMax 逐出最旧键，防长期运行内存无界。
     */
    private final Set<String> alerted = Collections.synchronizedSet(Collections.newSetFromMap(
            new LinkedHashMap<>(256, 0.75f, false) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > alertCacheMax;
                }
            }));

    @PostConstruct
    void init() {
        lastScan = LocalDateTime.now();
    }

    @Scheduled(fixedDelayString = "${hermes.monitor.scan-fixed-delay-ms:60000}")
    public void scan() {
        if (!enabled) {
            return;
        }
        LocalDateTime from = lastScan;
        LocalDateTime now = LocalDateTime.now();

        try {
            scanNewOutcomes(from, now);
            scanStuck(now);
            scanSlow(from);
            scanOrderViolations(from);
            // 全部成功才推进 watermark；异常时保持不动，下轮重扫同一窗口
            lastScan = now;
        } catch (Exception e) {
            log.warn("流程监控扫描异常，watermark 不推进，下轮重扫: {}", e.getMessage(), e);
        }
    }

    /** 状态告警：窗口内出现终态 FAILED 的步骤 */
    private void scanNewOutcomes(LocalDateTime from, LocalDateTime to) {
        stepMapper.selectList(new LambdaQueryWrapper<FlowStepLog>()
                        .ge(FlowStepLog::getEndTime, from)
                        .lt(FlowStepLog::getEndTime, to)
                        .eq(FlowStepLog::getStatus, "FAILED"))
                .forEach(step -> raise("STATE", step, "流程步骤失败",
                        "步骤 " + step.getStepName() + "（" + step.getStepCode() + "）失败: "
                                + safe(step.getErrorMessage())));
    }

    /** 时间告警（卡死）：RUNNING 超过阈值仍未结束 */
    private void scanStuck(LocalDateTime now) {
        LocalDateTime threshold = now.minusNanos(stuckStepMs * 1_000_000L);
        stepMapper.selectList(new LambdaQueryWrapper<FlowStepLog>()
                        .eq(FlowStepLog::getStatus, "RUNNING")
                        .lt(FlowStepLog::getStartTime, threshold))
                .forEach(step -> raise("STUCK", step, "流程步骤卡死",
                        "步骤 " + step.getStepName() + "（" + step.getStepCode() + "）运行超过 "
                                + (stuckStepMs / 60000) + " 分钟未结束"));
    }

    /** 时间告警（慢步骤）：窗口内结束且耗时超阈值 */
    private void scanSlow(LocalDateTime from) {
        stepMapper.selectList(new LambdaQueryWrapper<FlowStepLog>()
                        .ge(FlowStepLog::getEndTime, from)
                        .isNotNull(FlowStepLog::getDurationMs)
                        .gt(FlowStepLog::getDurationMs, slowStepMs))
                .forEach(step -> raise("SLOW", step, "流程步骤耗时超阈值",
                        "步骤 " + step.getStepName() + "（" + step.getStepCode() + "）耗时 "
                                + step.getDurationMs() + "ms，超过阈值 " + slowStepMs + "ms"));
    }

    /** 时间先后：窗口内开始的步骤早于其上游结束 */
    private void scanOrderViolations(LocalDateTime from) {
        stepMapper.selectList(new LambdaQueryWrapper<FlowStepLog>()
                        .ge(FlowStepLog::getStartTime, from)
                        .isNotNull(FlowStepLog::getUpstreamStep))
                .forEach(step -> {
                    if (step.getStartTime() == null) {
                        return;
                    }
                    FlowStepLog upstream = stepMapper.selectOne(new LambdaQueryWrapper<FlowStepLog>()
                            .eq(FlowStepLog::getRunId, step.getRunId())
                            .eq(FlowStepLog::getStepCode, step.getUpstreamStep())
                            .orderByDesc(FlowStepLog::getEndTime)
                            .last("limit 1"));
                    if (upstream != null && upstream.getEndTime() != null
                            && step.getStartTime().isBefore(upstream.getEndTime())) {
                        raise("ORDER", step, "流程时序倒挂",
                                "步骤 " + step.getStepName() + " 开始于 " + step.getStartTime()
                                        + "，早于其上游 " + step.getUpstreamStep()
                                        + " 的结束时间 " + upstream.getEndTime());
                    }
                });
    }

    private void raise(String type, FlowStepLog step, String title, String message) {
        String key = type + ":" + step.getId();
        if (!alerted.add(key)) {
            return;
        }
        String full = "[run=" + step.getRunId() + "] " + message;
        log.warn("[监控告警/{}] {} - {}", type, title, full);
        if (alertRecipient == null || alertRecipient.isBlank()) {
            log.warn("[监控告警] 未配置 hermes.monitor.alert-recipient，告警仅落日志");
            return;
        }
        try {
            gateway.sendByType(alertChannelType, alertRecipient, "[HERMES] " + title, full);
        } catch (Exception e) {
            log.warn("[监控告警] 发送失败（不影响扫描）: {}", e.getMessage());
        }
    }

    private String safe(String text) {
        return text == null ? "(无错误原因)" : text;
    }
}
