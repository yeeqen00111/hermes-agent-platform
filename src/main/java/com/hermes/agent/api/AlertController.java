package com.hermes.agent.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hermes.agent.entity.AlertRecord;
import com.hermes.agent.entity.AlertRule;
import com.hermes.agent.entity.LogRetention;
import com.hermes.agent.ops.AlertRetentionJob;
import com.hermes.agent.service.AlertRuleService;
import com.hermes.agent.service.LogGradeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 智能运维 API（白板·业务层）：日志告警规则（告警/白名单/提级）、告警监控、
 * 告警记录、日志清理策略，以及固化引擎的日志注入入口（生产由 kafka 消费调用同一引擎）。
 */
@RestController
@RequestMapping("/api/ops/alert")
@RequiredArgsConstructor
public class AlertController {

    private final AlertRuleService alertRuleService;
    private final LogGradeService logGradeService;
    private final AlertRetentionJob alertRetentionJob;
    private final ObjectMapper objectMapper;

    // ---------- 规则（告警 / 白名单 / 提级） ----------

    @GetMapping("/rules")
    public List<AlertRule> listRules(@RequestParam(required = false) String type) {
        return alertRuleService.listRules(type);
    }

    @PostMapping("/rules")
    public AlertRule saveRule(@RequestBody AlertRule rule) {
        return alertRuleService.saveRule(rule);
    }

    @DeleteMapping("/rules/{id}")
    public Map<String, Object> deleteRule(@PathVariable Long id) {
        alertRuleService.deleteRule(id);
        return Map.of("success", true);
    }

    // ---------- 保留策略（日志清理 3/7/7） ----------

    @GetMapping("/retention")
    public List<LogRetention> listRetention() {
        return alertRuleService.listRetention();
    }

    @PostMapping("/retention")
    public LogRetention saveRetention(@RequestBody LogRetention retention) {
        return alertRuleService.saveRetention(retention);
    }

    @PostMapping("/clean")
    public Map<String, Integer> clean() {
        return alertRetentionJob.clean();
    }

    // ---------- 告警记录 / 监控 ----------

    @GetMapping("/records")
    public List<AlertRecord> listRecords(@RequestParam(required = false) String projectName,
                                         @RequestParam(required = false) String type,
                                         @RequestParam(required = false, defaultValue = "100") int limit) {
        return alertRuleService.listRecords(projectName, type, limit);
    }

    @GetMapping("/monitor")
    public Map<String, Object> monitor() {
        return alertRuleService.monitor();
    }

    // ---------- 固化引擎入口（dev/联调注入；生产接 kafka） ----------

    @PostMapping("/ingest")
    public Map<String, Object> ingest(@RequestBody Map<String, Object> body) throws Exception {
        Object raw = body.get("raw");
        String rawJson;
        if (raw instanceof String s) {
            rawJson = s;
        } else if (raw != null) {
            rawJson = objectMapper.writeValueAsString(raw);
        } else {
            Map<String, Object> payload = new LinkedHashMap<>(body);
            payload.remove("channelCode");
            rawJson = objectMapper.writeValueAsString(payload);
        }
        Object channel = body.get("channelCode");
        return logGradeService.ingest(rawJson, channel == null ? null : String.valueOf(channel));
    }
}
