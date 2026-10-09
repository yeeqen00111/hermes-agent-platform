package com.hermes.agent.api;

import com.hermes.agent.entity.AlertReport;
import com.hermes.agent.service.AlertReportService;
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
 * 【AI】智能告警报表 API（白板·业务层·智能运维）：
 * 发送频率 / 发送内容 / 提示词 / 发送通道；定时发送 + 人工选范围即时生成。
 */
@RestController
@RequestMapping("/api/ops/alert-report")
@RequiredArgsConstructor
public class AlertReportController {

    private final AlertReportService service;

    @GetMapping("/configs")
    public List<AlertReport> listReports() {
        return service.listReports();
    }

    @PostMapping("/configs")
    public AlertReport saveReport(@RequestBody AlertReport report) {
        return service.saveReport(report);
    }

    @DeleteMapping("/configs/{id}")
    public Map<String, Object> deleteReport(@PathVariable Long id) {
        service.deleteReport(id);
        return Map.of("success", true);
    }

    /** 人工选范围：仅生成预览，不发送 */
    @PostMapping("/preview")
    public Map<String, Object> preview(@RequestBody Map<String, Object> body) {
        Object prompt = body.get("prompt");
        return service.preview(prompt == null ? null : String.valueOf(prompt), body);
    }

    /** 立即生成并发送（人工选范围可覆盖 scope；不传则用配置范围） */
    @PostMapping("/configs/{code}/run")
    public Map<String, Object> run(@PathVariable String code,
                                   @RequestBody(required = false) Map<String, Object> override) {
        return service.run(code, override == null ? Map.of() : override);
    }
}
