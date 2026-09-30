package com.hermes.agent.api;

import com.hermes.agent.monitor.DashboardQueryService;
import com.hermes.agent.monitor.HalfDayReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 看板监控数据面（白板：看板监控）。只读，供 Java 前端渲染；
 * 半天报表-AI 支持人工触发（白板「人工选范围」）。
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardQueryService queryService;
    private final HalfDayReportService reportService;

    @GetMapping("/overview")
    public Map<String, Object> overview(@RequestParam(defaultValue = "12") int windowHours) {
        return queryService.overview(windowHours);
    }

    @GetMapping("/runs")
    public List<Map<String, Object>> runs(@RequestParam(defaultValue = "24") int windowHours,
                                          @RequestParam(defaultValue = "20") int limit) {
        return queryService.recentRuns(windowHours, limit);
    }

    @GetMapping("/runs/{runId}/steps")
    public List<?> steps(@PathVariable String runId) {
        return queryService.runSteps(runId);
    }

    @GetMapping("/failures")
    public List<?> failures(@RequestParam(defaultValue = "24") int windowHours,
                            @RequestParam(defaultValue = "20") int limit) {
        return queryService.recentFailures(windowHours, limit);
    }

    @GetMapping("/order-violations")
    public List<Map<String, Object>> orderViolations(@RequestParam(defaultValue = "24") int windowHours) {
        return queryService.orderViolations(windowHours);
    }

    /** 人工触发半天报表：仅生成并返回正文，不发送（发送走定时任务） */
    @PostMapping("/half-day-report")
    public Map<String, Object> halfDayReport(@RequestParam(defaultValue = "12") int windowHours) {
        return Map.of("windowHours", windowHours, "report", reportService.generate(windowHours));
    }
}
