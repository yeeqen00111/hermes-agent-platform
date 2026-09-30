package com.hermes.agent.monitor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hermes.agent.entity.FlowRun;
import com.hermes.agent.entity.FlowStepLog;
import com.hermes.agent.mapper.FlowRunMapper;
import com.hermes.agent.mapper.FlowStepLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 看板监控数据面（白板：看板监控）。只读聚合 ai_flow_run / ai_flow_step_log，
 * 供 Java 前端渲染；本服务不做告警判定（那是 FlowMonitorService 的事）。
 */
@Service
@RequiredArgsConstructor
public class DashboardQueryService {

    private final FlowRunMapper runMapper;
    private final FlowStepLogMapper stepMapper;

    public Map<String, Object> overview(int windowHours) {
        LocalDateTime from = LocalDateTime.now().minusHours(windowHours);
        List<FlowRun> runs = runsSince(from);
        List<FlowStepLog> steps = stepsSince(from);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("windowHours", windowHours);
        result.put("from", from.toString());
        result.put("runTotal", runs.size());
        result.put("runByStatus", countBy(runs.stream().map(FlowRun::getStatus).collect(Collectors.toList())));
        result.put("stepTotal", steps.size());
        result.put("stepByStatus", countBy(steps.stream().map(FlowStepLog::getStatus).collect(Collectors.toList())));
        result.put("stepFailed", steps.stream().filter(s -> "FAILED".equals(s.getStatus())).count());
        result.put("runAvgDurationMs", avg(runs.stream().map(FlowRun::getDurationMs).collect(Collectors.toList())));
        result.put("stepAvgDurationMs", avg(steps.stream().map(FlowStepLog::getDurationMs).collect(Collectors.toList())));
        result.put("orderViolations", orderViolations(steps).size());
        return result;
    }

    public List<Map<String, Object>> recentRuns(int windowHours, int limit) {
        LocalDateTime from = LocalDateTime.now().minusHours(windowHours);
        List<FlowRun> runs = runMapper.selectList(new LambdaQueryWrapper<FlowRun>()
                .ge(FlowRun::getCreateTime, from)
                .orderByDesc(FlowRun::getCreateTime)
                .last("limit " + Math.max(1, Math.min(limit, 200))));
        List<FlowStepLog> allSteps = stepsSince(from);

        List<Map<String, Object>> items = new ArrayList<>();
        for (FlowRun run : runs) {
            List<FlowStepLog> runSteps = allSteps.stream()
                    .filter(s -> run.getRunId().equals(s.getRunId()))
                    .collect(Collectors.toList());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("runId", run.getRunId());
            item.put("agentCode", run.getAgentCode());
            item.put("bizKey", run.getBizKey());
            item.put("status", run.getStatus());
            item.put("startTime", run.getStartTime());
            item.put("endTime", run.getEndTime());
            item.put("durationMs", run.getDurationMs());
            item.put("errorMessage", run.getErrorMessage());
            item.put("stepTotal", runSteps.size());
            item.put("stepFailed", runSteps.stream().filter(s -> "FAILED".equals(s.getStatus())).count());
            items.add(item);
        }
        return items;
    }

    public List<FlowStepLog> runSteps(String runId) {
        return stepMapper.selectList(new LambdaQueryWrapper<FlowStepLog>()
                .eq(FlowStepLog::getRunId, runId)
                .orderByAsc(FlowStepLog::getStartTime));
    }

    public List<FlowStepLog> recentFailures(int windowHours, int limit) {
        LocalDateTime from = LocalDateTime.now().minusHours(windowHours);
        return stepMapper.selectList(new LambdaQueryWrapper<FlowStepLog>()
                .ge(FlowStepLog::getCreateTime, from)
                .eq(FlowStepLog::getStatus, "FAILED")
                .orderByDesc(FlowStepLog::getCreateTime)
                .last("limit " + Math.max(1, Math.min(limit, 200))));
    }

    /**
     * 时间先后（白板）：下游步骤开始时间早于其上游步骤结束时间 = 时序倒挂。
     */
    public List<Map<String, Object>> orderViolations(int windowHours) {
        return orderViolations(stepsSince(LocalDateTime.now().minusHours(windowHours)));
    }

    private List<Map<String, Object>> orderViolations(List<FlowStepLog> steps) {
        Map<String, List<FlowStepLog>> byRun = steps.stream()
                .collect(Collectors.groupingBy(FlowStepLog::getRunId));
        List<Map<String, Object>> violations = new ArrayList<>();
        for (List<FlowStepLog> runSteps : byRun.values()) {
            Map<String, FlowStepLog> byCode = runSteps.stream()
                    .collect(Collectors.toMap(FlowStepLog::getStepCode, s -> s, (a, b) -> a));
            for (FlowStepLog step : runSteps) {
                FlowStepLog upstream = step.getUpstreamStep() == null ? null : byCode.get(step.getUpstreamStep());
                if (upstream != null && upstream.getEndTime() != null && step.getStartTime() != null
                        && step.getStartTime().isBefore(upstream.getEndTime())) {
                    Map<String, Object> v = new LinkedHashMap<>();
                    v.put("runId", step.getRunId());
                    v.put("stepCode", step.getStepCode());
                    v.put("stepStartTime", step.getStartTime());
                    v.put("upstreamStep", upstream.getStepCode());
                    v.put("upstreamEndTime", upstream.getEndTime());
                    violations.add(v);
                }
            }
        }
        violations.sort((a, b) -> String.valueOf(b.get("stepStartTime")).compareTo(String.valueOf(a.get("stepStartTime"))));
        return violations;
    }

    List<FlowRun> runsSince(LocalDateTime from) {
        return runMapper.selectList(new LambdaQueryWrapper<FlowRun>()
                .ge(FlowRun::getCreateTime, from));
    }

    List<FlowStepLog> stepsSince(LocalDateTime from) {
        return stepMapper.selectList(new LambdaQueryWrapper<FlowStepLog>()
                .ge(FlowStepLog::getCreateTime, from));
    }

    private Map<String, Long> countBy(List<String> values) {
        return values.stream().collect(Collectors.groupingBy(v -> v == null ? "UNKNOWN" : v, Collectors.counting()));
    }

    private Long avg(List<Long> values) {
        List<Long> nonNull = values.stream().filter(v -> v != null && v > 0).collect(Collectors.toList());
        if (nonNull.isEmpty()) {
            return 0L;
        }
        return nonNull.stream().mapToLong(Long::longValue).sum() / nonNull.size();
    }
}
