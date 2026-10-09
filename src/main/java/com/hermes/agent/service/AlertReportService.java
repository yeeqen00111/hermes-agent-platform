package com.hermes.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hermes.agent.entity.AgentProfile;
import com.hermes.agent.entity.AlertRecord;
import com.hermes.agent.entity.AlertReport;
import com.hermes.agent.llm.LlmGateway;
import com.hermes.agent.llm.LlmMessage;
import com.hermes.agent.mapper.AlertRecordMapper;
import com.hermes.agent.mapper.AlertReportMapper;
import com.hermes.agent.notify.NotificationGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 【AI】智能告警报表（白板·业务层·智能运维）：
 * 按发送频率聚合告警记录 → 交 LLM 依提示词生成 markdown 报表 → 经发送通道发出。
 * 支持「人工选范围」即时生成/发送（可覆盖回溯小时、项目、类型、级别）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertReportService {

    private static final String SYSTEM_PROMPT =
            "你是运维告警分析助手。请基于给定的告警数据输出简明中文分析（要点式，先结论后建议），不要编造数据。";

    private final AlertReportMapper reportMapper;
    private final AlertRecordMapper alertRecordMapper;
    private final NotificationGateway notificationGateway;
    private final LlmGateway llmGateway;
    private final ObjectMapper objectMapper;

    // ---------- 配置 ----------

    public List<AlertReport> listReports() {
        return reportMapper.selectList(new LambdaQueryWrapper<AlertReport>()
                .orderByAsc(AlertReport::getId));
    }

    public AlertReport saveReport(AlertReport report) {
        if (report.getFrequencyHours() == null || report.getFrequencyHours() < 1) {
            report.setFrequencyHours(2);
        }
        if (report.getEnabled() == null) {
            report.setEnabled(1);
        }
        if (report.getLastSendTime() == null) {
            report.setLastSendTime(LocalDateTime.now());
        }
        if (report.getId() != null) {
            reportMapper.updateById(report);
        } else {
            reportMapper.insert(report);
        }
        return report;
    }

    public void deleteReport(Long id) {
        reportMapper.deleteById(id);
    }

    // ---------- 生成 / 发送 ----------

    /** 人工选范围：仅生成，不发送 */
    public Map<String, Object> preview(String prompt, Map<String, Object> override) {
        Map<String, Object> scope = mergeScope(null, override);
        String markdown = generate(scope, prompt);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        out.put("recordCount", collect(scope).size());
        out.put("markdown", markdown);
        return out;
    }

    /** 立即生成并发送（人工选范围可传 override；定时任务不传） */
    public Map<String, Object> run(String code, Map<String, Object> override) {
        AlertReport report = reportMapper.selectOne(new LambdaQueryWrapper<AlertReport>()
                .eq(AlertReport::getCode, code));
        if (report == null) {
            return Map.of("success", false, "message", "报表不存在: " + code);
        }
        Map<String, Object> scope = mergeScope(report.getScope(), override);
        String markdown = generate(scope, report.getPrompt());

        String status = "NO_CHANNEL";
        boolean sent = false;
        if (report.getChannelCode() != null && !report.getChannelCode().isBlank()) {
            sent = notificationGateway.sendByCode(report.getChannelCode(),
                    report.getRecipient() == null ? "" : report.getRecipient(),
                    "[HERMES告警报表] " + report.getName(), markdown);
            status = sent ? "SENT" : "FAILED";
        }
        report.setLastSendTime(LocalDateTime.now());
        report.setLastContent(markdown);
        reportMapper.updateById(report);
        log.info("告警报表已生成: code={}, status={}", code, status);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        out.put("sent", sent);
        out.put("status", status);
        out.put("markdown", markdown);
        return out;
    }

    // ---------- 生成逻辑 ----------

    private String generate(Map<String, Object> scope, String prompt) {
        List<AlertRecord> records = collect(scope);
        String overview = buildOverview(scope, records);
        String analysis = llmGateway.chat(reportProfile(), List.of(
                LlmMessage.system(SYSTEM_PROMPT),
                LlmMessage.user((prompt == null || prompt.isBlank() ? "请分析以下告警并给出建议。" : prompt)
                        + "\n\n【告警数据】\n" + overview)));
        StringBuilder md = new StringBuilder();
        md.append("# 智能告警报表\n\n");
        md.append("> 生成时间：").append(LocalDateTime.now()).append("　范围：").append(scopeDesc(scope)).append("\n\n");
        md.append("## 数据概览\n\n").append(overview).append("\n");
        md.append("## AI 分析\n\n").append(analysis).append("\n");
        return md.toString();
    }

    private List<AlertRecord> collect(Map<String, Object> scope) {
        LocalDateTime since = LocalDateTime.now().minusHours(lookbackHours(scope));
        LambdaQueryWrapper<AlertRecord> wrapper = new LambdaQueryWrapper<AlertRecord>()
                .ge(AlertRecord::getCreateTime, since)
                .orderByDesc(AlertRecord::getId)
                .last("LIMIT 2000");
        String projectName = str(scope.get("projectName"));
        String alertType = str(scope.get("alertType"));
        String level = str(scope.get("level"));
        if (projectName != null) {
            wrapper.eq(AlertRecord::getProjectName, projectName);
        }
        if (alertType != null) {
            wrapper.eq(AlertRecord::getAlertType, alertType);
        }
        if (level != null) {
            wrapper.eq(AlertRecord::getLogLevel, level);
        }
        return alertRecordMapper.selectList(wrapper);
    }

    private String buildOverview(Map<String, Object> scope, List<AlertRecord> records) {
        Map<String, Integer> byType = new LinkedHashMap<>();
        Map<String, Integer> bySystem = new LinkedHashMap<>();
        Map<String, Integer> byLevel = new LinkedHashMap<>();
        Map<String, Integer> byRule = new LinkedHashMap<>();
        for (AlertRecord r : records) {
            bump(byType, r.getAlertType());
            bump(bySystem, r.getSystemName());
            bump(byLevel, r.getLogLevel());
            bump(byRule, r.getRuleCode());
        }
        StringBuilder sb = new StringBuilder();
        sb.append("- 回溯：近 ").append(lookbackHours(scope)).append(" 小时");
        if (str(scope.get("projectName")) != null) {
            sb.append("，项目=").append(str(scope.get("projectName")));
        }
        if (str(scope.get("alertType")) != null) {
            sb.append("，类型=").append(str(scope.get("alertType")));
        }
        if (str(scope.get("level")) != null) {
            sb.append("，级别=").append(str(scope.get("level")));
        }
        sb.append("\n");
        sb.append("- 告警总数：").append(records.size()).append("\n");
        sb.append("- 按类型：").append(byType.isEmpty() ? "无" : byType).append("\n");
        sb.append("- 按系统：").append(bySystem.isEmpty() ? "无" : bySystem).append("\n");
        sb.append("- 按级别：").append(byLevel.isEmpty() ? "无" : byLevel).append("\n");
        sb.append("- 按规则：").append(byRule.isEmpty() ? "无" : byRule).append("\n");
        sb.append("\n最近告警：\n");
        records.stream().limit(20).forEach(r -> sb.append("  - [")
                .append(r.getAlertType()).append("] ").append(r.getSystemName()).append(" ")
                .append(r.getContent() == null ? "" : r.getContent().replace("\n", " "))
                .append("\n"));
        return sb.toString();
    }

    private AgentProfile reportProfile() {
        AgentProfile profile = new AgentProfile();
        profile.setModelName("deepseek-chat");
        profile.setTemperature(0.3);
        profile.setMaxTokens(2000);
        profile.setTimeoutMs(60000);
        return profile;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mergeScope(String storedScope, Map<String, Object> override) {
        Map<String, Object> scope = new LinkedHashMap<>();
        if (storedScope != null && !storedScope.isBlank()) {
            try {
                scope.putAll(objectMapper.readValue(storedScope, Map.class));
            } catch (Exception e) {
                log.warn("报表范围 JSON 解析失败: {}", storedScope);
            }
        }
        if (override != null) {
            for (String key : List.of("projectName", "alertType", "level", "lookbackHours")) {
                Object v = override.get(key);
                if (v != null && !String.valueOf(v).isBlank()) {
                    scope.put(key, v);
                }
            }
        }
        return scope;
    }

    private int lookbackHours(Map<String, Object> scope) {
        Object v = scope.get("lookbackHours");
        try {
            return v == null ? 2 : Math.max(1, Integer.parseInt(String.valueOf(v)));
        } catch (NumberFormatException e) {
            return 2;
        }
    }

    private String scopeDesc(Map<String, Object> scope) {
        List<String> parts = new ArrayList<>();
        parts.add("近 " + lookbackHours(scope) + " 小时");
        if (str(scope.get("projectName")) != null) {
            parts.add("项目=" + str(scope.get("projectName")));
        }
        if (str(scope.get("alertType")) != null) {
            parts.add("类型=" + str(scope.get("alertType")));
        }
        if (str(scope.get("level")) != null) {
            parts.add("级别=" + str(scope.get("level")));
        }
        return String.join("，", parts);
    }

    private static void bump(Map<String, Integer> map, String key) {
        map.merge(key == null || key.isBlank() ? "未标注" : key, 1, Integer::sum);
    }

    private static String str(Object v) {
        if (v == null) {
            return null;
        }
        String s = String.valueOf(v);
        return s.isBlank() ? null : s;
    }
}
