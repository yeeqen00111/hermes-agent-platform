package com.hermes.agent.api;

import com.hermes.agent.entity.BizMetric;
import com.hermes.agent.entity.BizMetricSample;
import com.hermes.agent.service.BizMetricService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 业务指标监控 API（白板·业务层·智能运维）。
 */
@RestController
@RequestMapping("/api/ops/metric")
@RequiredArgsConstructor
public class BizMetricController {

    private final BizMetricService service;

    @GetMapping("/metrics")
    public List<BizMetric> listMetrics(@RequestParam(required = false) String systemName) {
        return service.listMetrics(systemName);
    }

    @PostMapping("/metrics")
    public BizMetric saveMetric(@RequestBody BizMetric metric) {
        return service.saveMetric(metric);
    }

    @DeleteMapping("/metrics/{id}")
    public Map<String, Object> deleteMetric(@PathVariable Long id) {
        service.deleteMetric(id);
        return Map.of("success", true);
    }

    @GetMapping("/samples")
    public List<BizMetricSample> listSamples(@RequestParam(required = false) String metricCode,
                                             @RequestParam(required = false, defaultValue = "100") int limit) {
        return service.listSamples(metricCode, limit);
    }

    @GetMapping("/monitor")
    public List<Map<String, Object>> monitor() {
        return service.monitor();
    }

    /** 采样注入（dev/联调；生产由埋点/SQL/接口推送同一入口） */
    @PostMapping("/ingest")
    public Map<String, Object> ingest(@RequestBody Map<String, Object> body) {
        String code = body.get("code") == null ? null : String.valueOf(body.get("code"));
        Double value = body.get("value") == null ? null : Double.valueOf(String.valueOf(body.get("value")));
        LocalDateTime time = body.get("sampleTime") == null ? null
                : LocalDateTime.parse(String.valueOf(body.get("sampleTime")));
        return service.ingest(code, value, time);
    }
}
