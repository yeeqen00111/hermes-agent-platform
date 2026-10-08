package com.hermes.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hermes.agent.entity.AlertRecord;
import com.hermes.agent.entity.AlertRule;
import com.hermes.agent.entity.LogIngestStat;
import com.hermes.agent.entity.LogRetention;
import com.hermes.agent.mapper.AlertRecordMapper;
import com.hermes.agent.mapper.AlertRuleMapper;
import com.hermes.agent.mapper.LogIngestStatMapper;
import com.hermes.agent.mapper.LogRetentionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 智能运维 · 告警规则/白名单/提级 的管理面与告警监控取数。
 * 固化判定逻辑见 {@link LogGradeService}。
 */
@Service
@RequiredArgsConstructor
public class AlertRuleService {

    /** 监控聚合的取数上限（最近 N 条），避免无界扫描 */
    private static final int AGGREGATE_LIMIT = 2000;

    private final AlertRuleMapper alertRuleMapper;
    private final AlertRecordMapper alertRecordMapper;
    private final LogRetentionMapper retentionMapper;
    private final LogIngestStatMapper ingestStatMapper;

    // ---------- 规则 ----------

    public List<AlertRule> listRules(String ruleType) {
        LambdaQueryWrapper<AlertRule> wrapper = new LambdaQueryWrapper<AlertRule>()
                .orderByAsc(AlertRule::getPriority)
                .orderByAsc(AlertRule::getId);
        if (ruleType != null && !ruleType.isBlank()) {
            wrapper.eq(AlertRule::getRuleType, ruleType);
        }
        return alertRuleMapper.selectList(wrapper);
    }

    public AlertRule saveRule(AlertRule rule) {
        String type = rule.getRuleType() == null ? "ALERT" : rule.getRuleType().toUpperCase();
        rule.setRuleType(type);
        if (rule.getPriority() == null) {
            rule.setPriority(switch (type) {
                case "WHITELIST" -> 2;
                case "PROMOTE" -> 3;
                default -> 1;
            });
        }
        if (rule.getEnabled() == null) {
            rule.setEnabled(1);
        }
        if (rule.getId() != null) {
            alertRuleMapper.updateById(rule);
        } else {
            alertRuleMapper.insert(rule);
        }
        return rule;
    }

    public void deleteRule(Long id) {
        alertRuleMapper.deleteById(id);
    }

    // ---------- 保留策略（日志清理 3/7/7） ----------

    public List<LogRetention> listRetention() {
        return retentionMapper.selectList(new LambdaQueryWrapper<LogRetention>()
                .orderByAsc(LogRetention::getId));
    }

    public LogRetention saveRetention(LogRetention retention) {
        if (retention.getId() != null) {
            retentionMapper.updateById(retention);
        } else {
            retentionMapper.insert(retention);
        }
        return retention;
    }

    // ---------- 告警记录 / 监控 ----------

    public List<AlertRecord> listRecords(String projectName, String alertType, int limit) {
        LambdaQueryWrapper<AlertRecord> wrapper = new LambdaQueryWrapper<AlertRecord>()
                .orderByDesc(AlertRecord::getId);
        if (projectName != null && !projectName.isBlank()) {
            wrapper.eq(AlertRecord::getProjectName, projectName);
        }
        if (alertType != null && !alertType.isBlank()) {
            wrapper.eq(AlertRecord::getAlertType, alertType);
        }
        wrapper.last("LIMIT " + Math.max(1, Math.min(limit, 500)));
        return alertRecordMapper.selectList(wrapper);
    }

    /**
     * 告警监控：维度统计（按系统/服务器/日志级别）、触发次数、上次接收过滤时间、历史记录。
     */
    public Map<String, Object> monitor() {
        List<LogIngestStat> stats = ingestStatMapper.selectList(new LambdaQueryWrapper<LogIngestStat>()
                .orderByAsc(LogIngestStat::getId));
        List<AlertRecord> recent = alertRecordMapper.selectList(new LambdaQueryWrapper<AlertRecord>()
                .orderByDesc(AlertRecord::getId)
                .last("LIMIT " + AGGREGATE_LIMIT));

        Map<String, Integer> byRule = new LinkedHashMap<>();
        Map<String, Integer> bySystem = new LinkedHashMap<>();
        Map<String, Integer> byServer = new LinkedHashMap<>();
        Map<String, Integer> byLevel = new LinkedHashMap<>();
        for (AlertRecord r : recent) {
            if ("SUPPRESSED".equals(r.getAlertType())) {
                continue; // 拦截记录不计入告警触发次数
            }
            bump(byRule, key(r.getRuleCode(), r.getAlertType()));
            bump(bySystem, key(r.getSystemName(), null));
            bump(byServer, key(r.getServerName(), null));
            bump(byLevel, key(r.getLogLevel(), null));
        }

        List<Map<String, Object>> history = new ArrayList<>();
        for (int i = 0; i < Math.min(recent.size(), 50); i++) {
            AlertRecord r = recent.get(i);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", r.getId());
            row.put("ruleCode", r.getRuleCode());
            row.put("projectName", r.getProjectName());
            row.put("systemName", r.getSystemName());
            row.put("serverName", r.getServerName());
            row.put("logLevel", r.getLogLevel());
            row.put("alertType", r.getAlertType());
            row.put("status", r.getStatus());
            row.put("content", r.getContent());
            row.put("logTime", r.getLogTime());
            row.put("createTime", r.getCreateTime());
            history.add(row);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("stats", stats);
        out.put("byRule", toRows("rule", byRule));
        out.put("bySystem", toRows("systemName", bySystem));
        out.put("byServer", toRows("serverName", byServer));
        out.put("byLevel", toRows("logLevel", byLevel));
        out.put("history", history);
        return out;
    }

    private static List<Map<String, Object>> toRows(String dimName, Map<String, Integer> counts) {
        List<Map<String, Object>> rows = new ArrayList<>();
        counts.forEach((k, v) -> rows.add(new LinkedHashMap<>(Map.of(dimName, k, "count", v))));
        return rows;
    }

    private static void bump(Map<String, Integer> map, String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        map.merge(key, 1, Integer::sum);
    }

    private static String key(String value, String suffix) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return suffix == null ? value : value + " · " + suffix;
    }
}
