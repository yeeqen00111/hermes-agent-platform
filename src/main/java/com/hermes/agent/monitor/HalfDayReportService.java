package com.hermes.agent.monitor;

import com.hermes.agent.entity.AgentProfile;
import com.hermes.agent.entity.FlowStepLog;
import com.hermes.agent.llm.LlmGateway;
import com.hermes.agent.llm.LlmMessage;
import com.hermes.agent.notify.NotificationGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 半天报表-AI（白板）：每半天聚合流程执行情况，交大模型生成 markdown 报表，
 * 经平台通知网关发送。也可通过 /api/dashboard/half-day-report 人工触发（人工选窗口）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HalfDayReportService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DashboardQueryService queryService;
    private final LlmGateway llmGateway;
    private final NotificationGateway gateway;

    @Value("${hermes.monitor.report-model-provider:deepseek}")
    private String reportModelProvider;

    @Value("${hermes.monitor.report-model-name:deepseek-chat}")
    private String reportModelName;

    @Value("${hermes.monitor.report-window-hours:12}")
    private int defaultWindowHours;

    @Value("${hermes.monitor.alert-channel-type:FEISHU}")
    private String alertChannelType;

    @Value("${hermes.monitor.alert-recipient:}")
    private String alertRecipient;

    @Scheduled(cron = "${hermes.monitor.report-cron:0 0 9,15 * * *}")
    public void scheduled() {
        generateAndSend(defaultWindowHours);
    }

    public void generateAndSend(int windowHours) {
        Map<String, Object> stats = collect(windowHours);
        long runTotal = ((Number) stats.get("runTotal")).longValue();
        if (runTotal == 0) {
            log.info("[半天报表] 窗口 {}h 内无流程执行，跳过发送", windowHours);
            return;
        }
        String report = generate(stats);
        if (alertRecipient == null || alertRecipient.isBlank()) {
            log.warn("[半天报表] 未配置 hermes.monitor.alert-recipient，报表仅落日志");
            return;
        }
        gateway.sendByType(alertChannelType, alertRecipient, "[HERMES] 半天流程报表", report);
    }

    /** 人工触发/预览：返回报表正文，不发送 */
    public String generate(int windowHours) {
        return generate(collect(windowHours));
    }

    private Map<String, Object> collect(int windowHours) {
        Map<String, Object> stats = new LinkedHashMap<>(queryService.overview(windowHours));
        List<FlowStepLog> failures = queryService.recentFailures(windowHours, 20);
        stats.put("recentFailures", failures.stream().map(f -> Map.of(
                "runId", f.getRunId(),
                "step", f.getStepName(),
                "error", f.getErrorMessage() == null ? "" : f.getErrorMessage(),
                "time", String.valueOf(f.getEndTime()))).toList());
        stats.put("orderViolationDetails", queryService.orderViolations(windowHours));
        return stats;
    }

    private String generate(Map<String, Object> stats) {
        String now = LocalDateTime.now().format(TS);
        String prompt = "以下是 HERMES 平台最近 " + stats.get("windowHours") + " 小时的流程执行统计（JSON）。"
                + "请生成一份简明的中文 markdown 运维报表，包含：总体情况、失败步骤分析（如有）、"
                + "耗时情况、时序异常（如有）、以及需要人工关注的事项。不要编造 JSON 之外的数据。\n\n"
                + stats;
        AgentProfile profile = new AgentProfile();
        profile.setModelProvider(reportModelProvider);
        profile.setModelName(reportModelName);
        profile.setTemperature(0.2);
        String body = llmGateway.chat(profile, List.of(
                LlmMessage.system("你是 HERMES 平台的运维报表助手，输出纯 markdown 报表正文。"),
                LlmMessage.user(prompt)));
        return "# 半天流程报表（" + now + "，窗口 " + stats.get("windowHours") + "h）\n\n" + body;
    }
}
