package com.hermes.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hermes.agent.entity.AlertRecord;
import com.hermes.agent.entity.AlertRule;
import com.hermes.agent.entity.LogIngestStat;
import com.hermes.agent.entity.LogParseRule;
import com.hermes.agent.mapper.AlertRecordMapper;
import com.hermes.agent.mapper.AlertRuleMapper;
import com.hermes.agent.mapper.LogIngestStatMapper;
import com.hermes.agent.mapper.LogParseRuleMapper;
import com.hermes.agent.notify.NotificationGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 日志分级-固化引擎（白板·业务层·智能运维）：
 * 原始日志 JSON →（五级解析）→（按优先级固化：1 告警规则 → 2 告警白名单 → 3 提级告警）
 * → 落告警记录 + 发送告警（通道/接收人）+ 更新接收过滤统计。
 * <p>数据来源可插拔：dev 用 {@code /api/ops/alert/ingest} 注入，生产接 kafka 消费（同一入口）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogGradeService {

    private final LogCollectService logCollectService;
    private final LogParseRuleMapper parseRuleMapper;
    private final AlertRuleMapper alertRuleMapper;
    private final AlertRecordMapper alertRecordMapper;
    private final LogIngestStatMapper ingestStatMapper;
    private final NotificationGateway notificationGateway;

    /**
     * 固化一条原始日志。
     *
     * @param rawJson     原始 JSON 日志
     * @param channelCode 采集通道编码（用于选择解析规则；可空）
     */
    @Transactional
    public Map<String, Object> ingest(String rawJson, String channelCode) {
        LogParseRule parseRule = resolveParseRule(channelCode);
        if (parseRule == null) {
            return Map.of("success", false, "message", "无启用的日志解析规则（先去「日志采集」配置）");
        }
        Map<String, Object> parsed = logCollectService.parse(parseRule, rawJson);
        if (!Boolean.TRUE.equals(parsed.get("success"))) {
            return parsed;
        }

        String system = str(parsed.get("system"), "unknown");
        String level = str(parsed.get("level"), "");
        String content = str(parsed.get("content"), rawJson);
        String server = str(parsed.get("service"), system);
        // 分项目一套配置：以系统作为项目维度
        String project = system;
        LocalDateTime logTime = parseTime(str(parsed.get("time"), null), parseRule.getTimeFormat());

        AlertRule alertHit = null;
        AlertRule whitelistHit = null;
        AlertRule promoteHit = null;
        List<AlertRule> rules = alertRuleMapper.selectList(new LambdaQueryWrapper<AlertRule>()
                .eq(AlertRule::getEnabled, 1)
                .orderByAsc(AlertRule::getPriority)
                .orderByAsc(AlertRule::getId));
        for (AlertRule rule : rules) {
            if (!isBlank(rule.getProjectName()) && !rule.getProjectName().equals(project)) {
                continue; // 项目不匹配
            }
            if (!matches(rule, level, content)) {
                continue;
            }
            String type = rule.getRuleType() == null ? "ALERT" : rule.getRuleType().toUpperCase();
            switch (type) {
                case "WHITELIST" -> whitelistHit = whitelistHit == null ? rule : whitelistHit;
                case "PROMOTE" -> promoteHit = promoteHit == null ? rule : promoteHit;
                default -> alertHit = alertHit == null ? rule : alertHit;
            }
        }

        String decision;
        AlertRule dest = null;
        if (whitelistHit != null) {
            decision = "SUPPRESSED";
        } else if (promoteHit != null) {
            decision = "PROMOTE";
            dest = promoteHit;
        } else if (alertHit != null) {
            decision = "ALERT";
            dest = alertHit;
        } else {
            decision = "IGNORED";
        }

        Long recordId = null;
        String status = null;
        boolean notified = false;
        if ("SUPPRESSED".equals(decision)) {
            recordId = writeRecord(whitelistHit, project, system, server, level, content, logTime,
                    "SUPPRESSED", "SUPPRESSED", null);
        } else if (dest != null) {
            status = "NO_CHANNEL";
            if (!isBlank(dest.getChannelCode())) {
                boolean ok = notificationGateway.sendByCode(dest.getChannelCode(),
                        dest.getRecipient() == null ? "" : dest.getRecipient(),
                        "[HERMES告警·" + ("PROMOTE".equals(decision) ? "提级" : "告警") + "] " + system,
                        buildNotifyContent(level, logTime, content, dest));
                status = ok ? "SENT" : "FAILED";
                notified = ok;
            }
            recordId = writeRecord(dest, project, system, server, level, content, logTime,
                    decision, status, dest.getChannelCode());
        }

        updateStat(project, decision);

        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("success", true);
        out.put("project", project);
        out.put("system", system);
        out.put("level", level);
        out.put("decision", decision);
        out.put("ruleCode", dest != null ? dest.getCode() : (whitelistHit != null ? whitelistHit.getCode() : null));
        out.put("status", status);
        out.put("notified", notified);
        out.put("recordId", recordId);
        out.put("content", content);
        return out;
    }

    private Long writeRecord(AlertRule rule, String project, String system, String server, String level,
                             String content, LocalDateTime logTime, String alertType, String status, String channel) {
        AlertRecord record = new AlertRecord();
        record.setRuleCode(rule == null ? null : rule.getCode());
        record.setProjectName(project);
        record.setSystemName(system);
        record.setServerName(server);
        record.setLogLevel(level);
        record.setContent(content != null && content.length() > 4000 ? content.substring(0, 4000) : content);
        record.setLogTime(logTime);
        record.setAlertType(alertType);
        record.setStatus(status);
        record.setChannelCode(channel);
        record.setRecipient(rule == null ? null : rule.getRecipient());
        record.setCreateTime(LocalDateTime.now());
        alertRecordMapper.insert(record);
        return record.getId();
    }

    private void updateStat(String project, String decision) {
        LogIngestStat stat = ingestStatMapper.selectOne(new LambdaQueryWrapper<LogIngestStat>()
                .eq(LogIngestStat::getProjectName, project));
        boolean isNew = stat == null;
        if (isNew) {
            stat = new LogIngestStat();
            stat.setProjectName(project);
            stat.setTotalIngested(0);
            stat.setTotalAlert(0);
            stat.setTotalPromote(0);
            stat.setTotalSuppress(0);
        }
        stat.setTotalIngested(nvl(stat.getTotalIngested()) + 1);
        switch (decision) {
            case "ALERT" -> stat.setTotalAlert(nvl(stat.getTotalAlert()) + 1);
            case "PROMOTE" -> stat.setTotalPromote(nvl(stat.getTotalPromote()) + 1);
            case "SUPPRESSED" -> stat.setTotalSuppress(nvl(stat.getTotalSuppress()) + 1);
            default -> { }
        }
        stat.setLastIngestTime(LocalDateTime.now());
        stat.setUpdateTime(LocalDateTime.now());
        if (isNew) {
            ingestStatMapper.insert(stat);
        } else {
            ingestStatMapper.updateById(stat);
        }
    }

    private LogParseRule resolveParseRule(String channelCode) {
        LambdaQueryWrapper<LogParseRule> wrapper = new LambdaQueryWrapper<LogParseRule>()
                .eq(LogParseRule::getEnabled, 1);
        if (!isBlank(channelCode)) {
            wrapper.eq(LogParseRule::getChannelCode, channelCode);
        }
        List<LogParseRule> list = parseRuleMapper.selectList(wrapper.orderByAsc(LogParseRule::getId));
        if (list.isEmpty() && !isBlank(channelCode)) {
            // 通道未绑定专用规则时回退到通用规则
            list = parseRuleMapper.selectList(new LambdaQueryWrapper<LogParseRule>()
                    .eq(LogParseRule::getEnabled, 1).orderByAsc(LogParseRule::getId));
        }
        return list.isEmpty() ? null : list.get(0);
    }

    /** 规则匹配：级别过滤（逗号分隔）+ 内容正则（空=全部） */
    private boolean matches(AlertRule rule, String level, String content) {
        if (!isBlank(rule.getLevelFilter())) {
            boolean in = Arrays.stream(rule.getLevelFilter().split(","))
                    .map(String::trim)
                    .anyMatch(s -> s.equalsIgnoreCase(level == null ? "" : level));
            if (!in) {
                return false;
            }
        }
        if (isBlank(rule.getMatchPattern())) {
            return true;
        }
        String text = content == null ? "" : content;
        try {
            return Pattern.compile(rule.getMatchPattern(), Pattern.CASE_INSENSITIVE).matcher(text).find();
        } catch (PatternSyntaxException e) {
            return text.toLowerCase().contains(rule.getMatchPattern().toLowerCase());
        }
    }

    private LocalDateTime parseTime(String value, String format) {
        if (value == null || value.isBlank()) {
            return LocalDateTime.now();
        }
        try {
            if (format != null && !format.isBlank()) {
                return LocalDateTime.parse(value, DateTimeFormatter.ofPattern(format));
            }
        } catch (Exception ignored) {
            // 落到通用格式重试
        }
        try {
            return LocalDateTime.parse(value);
        } catch (Exception ignored) {
            // 继续尝试其它格式
        }
        try {
            return LocalDateTime.parse(value.replace(' ', 'T').replace("Z", ""));
        } catch (Exception ignored) {
            return LocalDateTime.now();
        }
    }

    private String buildNotifyContent(String level, LocalDateTime logTime, String content, AlertRule rule) {
        return "系统: " + rule.getProjectName()
                + "\n级别: " + level
                + "\n时间: " + logTime
                + "\n规则: " + rule.getName() + " (" + rule.getCode() + ")"
                + "\n内容: " + content;
    }

    private static int nvl(Integer v) {
        return v == null ? 0 : v;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String str(Object v, String fallback) {
        return v == null || String.valueOf(v).isBlank() ? fallback : String.valueOf(v);
    }
}
