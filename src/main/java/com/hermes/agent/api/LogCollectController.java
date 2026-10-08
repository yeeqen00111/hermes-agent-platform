package com.hermes.agent.api;

import com.hermes.agent.entity.LogChannel;
import com.hermes.agent.entity.LogParseRule;
import com.hermes.agent.service.LogCollectService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 日志采集 API（白板·系统管理）：采集通道 + JSON 解析规则。
 */
@RestController
@RequestMapping("/api/admin/log")
@RequiredArgsConstructor
public class LogCollectController {

    private final LogCollectService service;

    // ---------- 采集通道 ----------

    @GetMapping("/channels")
    public List<LogChannel> listChannels() {
        return service.listChannels();
    }

    @PostMapping("/channels")
    public LogChannel saveChannel(@RequestBody LogChannel channel) {
        return service.saveChannel(channel);
    }

    @DeleteMapping("/channels/{id}")
    public Map<String, Object> deleteChannel(@PathVariable Long id) {
        service.deleteChannel(id);
        return Map.of("success", true);
    }

    @GetMapping("/channels/{code}/filebeat-example")
    public Map<String, Object> filebeatExample(@PathVariable String code) {
        return service.filebeatExample(code);
    }

    // ---------- 解析规则 ----------

    @GetMapping("/parse-rules")
    public List<LogParseRule> listParseRules() {
        return service.listParseRules();
    }

    @PostMapping("/parse-rules")
    public LogParseRule saveParseRule(@RequestBody LogParseRule rule) {
        return service.saveParseRule(rule);
    }

    @DeleteMapping("/parse-rules/{id}")
    public Map<String, Object> deleteParseRule(@PathVariable Long id) {
        service.deleteParseRule(id);
        return Map.of("success", true);
    }

    @PostMapping("/parse-rules/preview")
    public Map<String, Object> preview(@RequestBody LogParseRule rule) {
        return service.preview(rule);
    }
}
