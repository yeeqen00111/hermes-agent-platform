package com.hermes.agent.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hermes.agent.dto.ToolRequest;
import com.hermes.agent.dto.ToolResponse;
import com.hermes.agent.entity.AlertHandle;
import com.hermes.agent.entity.AlertRecord;
import com.hermes.agent.entity.BizMetric;
import com.hermes.agent.entity.BizMetricSample;
import com.hermes.agent.mapper.AlertHandleMapper;
import com.hermes.agent.mapper.AlertRecordMapper;
import com.hermes.agent.mapper.BizMetricMapper;
import com.hermes.agent.mapper.BizMetricSampleMapper;
import com.hermes.agent.service.KnowledgeService;
import com.hermes.agent.service.NacosDataPlaneService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;

/**
 * 内置工具执行器（本平台自研数据面，全部返回真实数据、不造假数据）：
 * <ul>
 *   <li>log.*：日志分级-固化管线的固化记录（ai_alert_record，含白名单拦截记录），不含未固化原始日志</li>
 *   <li>alert.*：告警记录 + 追加式处理流水（ai_alert_handle）</li>
 *   <li>nacos.*：经 ai_nacos_server 调 Nacos OpenAPI（凭据只存环境变量引用）</li>
 *   <li>knowledge.search：本地知识库全文检索</li>
 *   <li>database.metric.query：本地指标库（ai_biz_metric + ai_biz_metric_sample）</li>
 *   <li>report.generate：本地数据聚合生成 markdown 健康报表</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BuiltinToolExecutor {

    private static final Set<String> SUPPORTED = Set.of(
            "log.search", "log.context", "log.aggregate",
            "alert.query", "alert.acknowledge", "alert.resolve", "alert.suppress",
            "nacos.change.query", "nacos.config.query", "nacos.instance.query",
            "knowledge.search", "database.metric.query", "report.generate");

    /** 工具侧告警等级 → 本地固化记录的日志级别 */
    private static final Map<String, String> SEVERITY_TO_LEVEL = Map.of(
            "P0", "ERROR", "P1", "ERROR", "P2", "WARN", "P3", "INFO");

    private static final DateTimeFormatter[] TIME_FORMATS = {
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ISO_LOCAL_DATE
    };

    private final AlertRecordMapper alertRecordMapper;
    private final AlertHandleMapper alertHandleMapper;
    private final KnowledgeService knowledgeService;
    private final BizMetricMapper bizMetricMapper;
    private final BizMetricSampleMapper bizMetricSampleMapper;
    private final NacosDataPlaneService nacosDataPlaneService;

    public boolean supports(String toolCode) {
        return SUPPORTED.contains(toolCode);
    }

    public ToolResponse execute(String toolCode, ToolRequest request, long startTime) {
        Map<String, Object> args = request.getArguments() == null ? Map.of() : request.getArguments();
        try {
            return dispatch(toolCode, args, request, startTime);
        } catch (IllegalArgumentException e) {
            return error(toolCode, "INVALID_ARGUMENT", e.getMessage(), startTime);
        } catch (Exception e) {
            log.error("工具 {} 执行失败", toolCode, e);
            return error(toolCode, "EXECUTION_ERROR", "工具执行失败: " + e.getMessage(), startTime);
        }
    }

    private ToolResponse dispatch(String toolCode, Map<String, Object> args,
                                  ToolRequest request, long startTime) {
        return switch (toolCode) {
            case "log.search" -> logSearch(args, startTime);
            case "log.context" -> logContext(args, startTime);
            case "log.aggregate" -> logAggregate(args, startTime);
            case "alert.query" -> alertQuery(args, startTime);
            case "alert.acknowledge" -> alertHandle(args, request, "CONFIRMED", startTime);
            case "alert.resolve" -> alertHandle(args, request, "RESOLVED", startTime);
            case "alert.suppress" -> alertHandle(args, request, "SUPPRESSED", startTime);
            case "nacos.change.query" -> nacos(startTime,
                    () -> nacosDataPlaneService.changeQuery(str(args, "environment"), str(args, "namespace"),
                            str(args, "group"), str(args, "dataId"), intOrNull(args, "limit")));
            case "nacos.config.query" -> nacos(startTime,
                    () -> nacosDataPlaneService.configQuery(str(args, "environment"), str(args, "namespace"),
                            str(args, "group"), required(args, "dataId")));
            case "nacos.instance.query" -> nacos(startTime,
                    () -> nacosDataPlaneService.instanceQuery(str(args, "environment"), str(args, "namespace"),
                            required(args, "service"), str(args, "cluster")));
            case "knowledge.search" -> knowledgeSearch(args, startTime);
            case "database.metric.query" -> metricQuery(args, startTime);
            case "report.generate" -> reportGenerate(args, startTime);
            default -> error(toolCode, "TOOL_NOT_IMPLEMENTED", "工具已注册但未实现执行器: " + toolCode, startTime);
        };
    }

    // ---------- log.* ----------

    private ToolResponse logSearch(Map<String, Object> args, long startTime) {
        String service = required(args, "service");
        LocalDateTime from = time(args, "from", true);
        LocalDateTime to = time(args, "to", true);
        int limit = intArg(args, "limit", 20, 100);

        LambdaQueryWrapper<AlertRecord> wrapper = new LambdaQueryWrapper<AlertRecord>()
                .and(w -> w.eq(AlertRecord::getServerName, service)
                        .or().eq(AlertRecord::getSystemName, service))
                .ge(AlertRecord::getLogTime, from)
                .le(AlertRecord::getLogTime, to)
                .orderByDesc(AlertRecord::getId)
                .last("LIMIT " + limit);
        String level = str(args, "level");
        if (level != null && !level.isBlank()) {
            wrapper.eq(AlertRecord::getLogLevel, level.trim().toUpperCase());
        }
        String keyword = str(args, "keyword");
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(AlertRecord::getContent, keyword);
        }
        List<AlertRecord> records = alertRecordMapper.selectList(wrapper);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", records.size());
        data.put("records", records.stream().map(this::recordRow).toList());
        note(data, "本地数据面为平台固化日志（含白名单拦截 SUPPRESSED 记录），不含未固化原始日志");
        if (str(args, "traceId") != null) {
            note(data, "固化记录不含 traceId 维度，该参数未参与过滤");
        }
        return ok("log.search", data, logCitations(records), startTime);
    }

    private ToolResponse logContext(Map<String, Object> args, long startTime) {
        long eventId = idArg(args, "eventId");
        AlertRecord target = alertRecordMapper.selectById(eventId);
        if (target == null) {
            return error("log.context", "EVENT_NOT_FOUND", "日志事件不存在: " + eventId, startTime);
        }
        int before = intArg(args, "before", 5, 50);
        int after = intArg(args, "after", 5, 50);
        List<AlertRecord> beforeRows = target.getServerName() == null ? List.of()
                : alertRecordMapper.selectList(new LambdaQueryWrapper<AlertRecord>()
                        .eq(AlertRecord::getServerName, target.getServerName())
                        .lt(AlertRecord::getId, eventId)
                        .orderByDesc(AlertRecord::getId)
                        .last("LIMIT " + before));
        List<AlertRecord> afterRows = target.getServerName() == null ? List.of()
                : alertRecordMapper.selectList(new LambdaQueryWrapper<AlertRecord>()
                        .eq(AlertRecord::getServerName, target.getServerName())
                        .gt(AlertRecord::getId, eventId)
                        .orderByAsc(AlertRecord::getId)
                        .last("LIMIT " + after));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("target", recordRow(target));
        data.put("before", beforeRows.stream().map(this::recordRow).toList());
        data.put("after", afterRows.stream().map(this::recordRow).toList());
        return ok("log.context", data, logCitations(List.of(target)), startTime);
    }

    private ToolResponse logAggregate(Map<String, Object> args, long startTime) {
        String service = required(args, "service");
        LocalDateTime from = time(args, "from", true);
        LocalDateTime to = time(args, "to", true);
        String groupBy = str(args, "groupBy");
        String dimension = groupBy == null || groupBy.isBlank() ? "level" : groupBy.trim().toLowerCase();
        if (!Set.of("level", "service").contains(dimension)) {
            throw new IllegalArgumentException("聚合维度仅支持 level/service（本地固化记录无 exceptionType 维度）");
        }
        List<AlertRecord> records = alertRecordMapper.selectList(new LambdaQueryWrapper<AlertRecord>()
                .and(w -> w.eq(AlertRecord::getServerName, service)
                        .or().eq(AlertRecord::getSystemName, service))
                .ge(AlertRecord::getLogTime, from)
                .le(AlertRecord::getLogTime, to)
                .last("LIMIT 5000"));

        Map<String, Integer> counts = new TreeMap<>();
        for (AlertRecord r : records) {
            String key = "level".equals(dimension) ? r.getLogLevel() : r.getServerName();
            counts.merge(key == null || key.isBlank() ? "(未知)" : key, 1, Integer::sum);
        }
        List<Map<String, Object>> buckets = counts.entrySet().stream()
                .map(e -> Map.<String, Object>of(dimension, e.getKey(), "count", e.getValue()))
                .toList();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("service", service);
        data.put("groupBy", dimension);
        data.put("scanned", records.size());
        data.put("buckets", buckets);
        note(data, "本地数据面为平台固化日志（含白名单拦截记录），统计口径为固化记录数");
        return ok("log.aggregate", data, null, startTime);
    }

    private Map<String, Object> recordRow(AlertRecord r) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("eventId", r.getId());
        row.put("systemName", r.getSystemName());
        row.put("serverName", r.getServerName());
        row.put("level", r.getLogLevel());
        row.put("content", r.getContent());
        row.put("time", r.getLogTime() == null ? null : r.getLogTime().toString());
        row.put("alertType", r.getAlertType());
        row.put("sendStatus", r.getStatus());
        return row;
    }

    // ---------- alert.* ----------

    private ToolResponse alertQuery(Map<String, Object> args, long startTime) {
        int limit = intArg(args, "limit", 50, 200);
        LambdaQueryWrapper<AlertRecord> wrapper = new LambdaQueryWrapper<AlertRecord>()
                .orderByDesc(AlertRecord::getId)
                .last("LIMIT " + limit);
        String severity = str(args, "severity");
        if (severity != null && !severity.isBlank()) {
            String mapped = SEVERITY_TO_LEVEL.get(severity.trim().toUpperCase());
            wrapper.eq(AlertRecord::getLogLevel, mapped == null ? severity.trim().toUpperCase() : mapped);
        }
        String service = str(args, "service");
        if (service != null && !service.isBlank()) {
            wrapper.and(w -> w.eq(AlertRecord::getServerName, service)
                    .or().eq(AlertRecord::getSystemName, service));
        }
        LocalDateTime from = time(args, "from", false);
        LocalDateTime to = time(args, "to", false);
        if (from != null) {
            wrapper.ge(AlertRecord::getLogTime, from);
        }
        if (to != null) {
            wrapper.le(AlertRecord::getLogTime, to);
        }
        List<AlertRecord> records = alertRecordMapper.selectList(wrapper);

        // 一次取回全部命中记录的处理流水，保留每条记录最新一次动作作为生效状态
        Map<Long, AlertHandle> latestHandle = new LinkedHashMap<>();
        if (!records.isEmpty()) {
            alertHandleMapper.selectList(new LambdaQueryWrapper<AlertHandle>()
                            .in(AlertHandle::getRecordId, records.stream().map(AlertRecord::getId).toList())
                            .orderByAsc(AlertHandle::getId))
                    .forEach(h -> latestHandle.put(h.getRecordId(), h));
        }

        String statusFilter = str(args, "status");
        List<Map<String, Object>> rows = new ArrayList<>();
        for (AlertRecord r : records) {
            Map<String, Object> row = recordRow(r);
            AlertHandle h = latestHandle.get(r.getId());
            String status = h == null ? "NEW" : h.getAction();
            if (statusFilter != null && !statusFilter.isBlank()
                    && !status.equalsIgnoreCase(statusFilter.trim())) {
                continue;
            }
            row.put("status", status);
            row.put("handleComment", h == null ? null : h.getComment());
            row.put("handleBy", h == null ? null : h.getHandleBy());
            row.put("handleTime", h == null ? null : h.getCreateTime().toString());
            rows.add(row);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", rows.size());
        data.put("records", rows);
        note(data, "状态口径：无处理流水为 NEW，最新一次处理动作为 CONFIRMED/RESOLVED/SUPPRESSED；"
                + "sendStatus 为通知回执（SENT/FAILED/NO_CHANNEL）");
        return ok("alert.query", data, alertCitations(records), startTime);
    }

    private ToolResponse alertHandle(Map<String, Object> args, ToolRequest request,
                                     String action, long startTime) {
        long alertId = idArg(args, "alertId");
        AlertRecord record = alertRecordMapper.selectById(alertId);
        if (record == null) {
            return error("alert." + action.toLowerCase(), "ALERT_NOT_FOUND",
                    "告警记录不存在: " + alertId, startTime);
        }
        AlertHandle handle = new AlertHandle();
        handle.setRecordId(alertId);
        handle.setAction(action);
        handle.setComment(str(args, "comment"));
        handle.setHandleBy(request.getUserId() == null ? null : String.valueOf(request.getUserId()));
        handle.setCreateTime(LocalDateTime.now());
        alertHandleMapper.insert(handle);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("alertId", alertId);
        data.put("action", action);
        data.put("handleId", handle.getId());
        if ("SUPPRESSED".equals(action)) {
            note(data, "本次静默仅记录处理流水；后续同类日志若需自动拦截，请到「告警白名单」维护匹配规则");
        }
        return ok("alert." + action.toLowerCase(), data, null, startTime);
    }

    // ---------- knowledge / metric / report ----------

    private ToolResponse knowledgeSearch(Map<String, Object> args, long startTime) {
        String query = required(args, "query");
        int topK = intArg(args, "topK", 5, 20);
        List<Map<String, Object>> hits = knowledgeService.search(query);
        List<Map<String, Object>> rows = hits.size() > topK ? hits.subList(0, topK) : hits;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", rows.size());
        data.put("results", rows);
        List<ToolResponse.Citation> citations = rows.stream().map(r -> {
            ToolResponse.Citation c = new ToolResponse.Citation();
            c.setKind("knowledge");
            c.setSource("ai_kb_node");
            c.setTitle(String.valueOf(r.get("title")));
            c.setLocator(String.valueOf(r.get("id")));
            c.setSnippet(String.valueOf(r.get("snippet")));
            return c;
        }).toList();
        return ok("knowledge.search", data, citations, startTime);
    }

    private ToolResponse metricQuery(Map<String, Object> args, long startTime) {
        String metric = required(args, "metric");
        LocalDateTime from = time(args, "from", true);
        LocalDateTime to = time(args, "to", true);

        BizMetric def = bizMetricMapper.selectOne(new LambdaQueryWrapper<BizMetric>()
                .eq(BizMetric::getCode, metric));
        if (def == null) {
            return error("database.metric.query", "METRIC_NOT_FOUND",
                    "指标不存在（先到「业务指标监控」定义）: " + metric, startTime);
        }
        List<BizMetricSample> samples = bizMetricSampleMapper.selectList(
                new LambdaQueryWrapper<BizMetricSample>()
                        .eq(BizMetricSample::getMetricCode, metric)
                        .ge(BizMetricSample::getSampleTime, from)
                        .le(BizMetricSample::getSampleTime, to)
                        .orderByAsc(BizMetricSample::getSampleTime)
                        .last("LIMIT 1000"));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("metric", metric);
        data.put("name", def.getName());
        data.put("systemName", def.getSystemName());
        data.put("metricType", def.getMetricType());
        data.put("unit", def.getUnit());
        data.put("threshold", def.getThresholdOp() + " " + def.getThresholdValue());
        data.put("total", samples.size());
        data.put("breachedCount", samples.stream()
                .filter(s -> Integer.valueOf(1).equals(s.getBreached())).count());
        data.put("samples", samples.stream().map(s -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("value", s.getMetricValue());
            row.put("breached", Integer.valueOf(1).equals(s.getBreached()));
            row.put("time", s.getSampleTime() == null ? null : s.getSampleTime().toString());
            return row;
        }).toList());
        note(data, "本地指标库为自研埋点采样（ai_biz_metric_sample），dimensions/filters 参数不参与过滤");
        return ok("database.metric.query", data, null, startTime);
    }

    private ToolResponse reportGenerate(Map<String, Object> args, long startTime) {
        String format = str(args, "format");
        if (format != null && !format.isBlank() && !"md".equalsIgnoreCase(format.trim())) {
            return error("report.generate", "FORMAT_UNSUPPORTED",
                    "本地报表仅支持 md 格式，请求格式: " + format, startTime);
        }
        LocalDateTime from = time(args, "from", true);
        LocalDateTime to = time(args, "to", true);

        List<AlertRecord> records = alertRecordMapper.selectList(new LambdaQueryWrapper<AlertRecord>()
                .ge(AlertRecord::getLogTime, from)
                .le(AlertRecord::getLogTime, to)
                .last("LIMIT 5000"));
        Map<String, Integer> byType = new TreeMap<>();
        Map<String, Integer> byLevel = new TreeMap<>();
        Map<String, Integer> bySystem = new TreeMap<>();
        for (AlertRecord r : records) {
            byType.merge(r.getAlertType() == null ? "(未分类)" : r.getAlertType(), 1, Integer::sum);
            byLevel.merge(r.getLogLevel() == null ? "(未知)" : r.getLogLevel(), 1, Integer::sum);
            bySystem.merge(r.getSystemName() == null ? "(未知)" : r.getSystemName(), 1, Integer::sum);
        }
        long confirmed = alertHandleMapper.selectCount(new LambdaQueryWrapper<AlertHandle>()
                .eq(AlertHandle::getAction, "CONFIRMED"));
        long resolved = alertHandleMapper.selectCount(new LambdaQueryWrapper<AlertHandle>()
                .eq(AlertHandle::getAction, "RESOLVED"));
        List<BizMetricSample> breaches = bizMetricSampleMapper.selectList(
                new LambdaQueryWrapper<BizMetricSample>()
                        .eq(BizMetricSample::getBreached, 1)
                        .ge(BizMetricSample::getSampleTime, from)
                        .le(BizMetricSample::getSampleTime, to)
                        .last("LIMIT 100"));

        StringBuilder md = new StringBuilder();
        md.append("# 系统健康报表\n\n窗口：").append(from).append(" ~ ").append(to).append("\n\n");
        md.append("## 总体情况\n\n");
        md.append("- 固化记录 ").append(records.size()).append(" 条");
        byType.forEach((k, v) -> md.append("，").append(k).append(" ").append(v).append(" 条"));
        md.append('\n');
        md.append("- 告警处理：确认 ").append(confirmed).append(" 次，解决 ").append(resolved).append(" 次\n");
        md.append("- 涉及系统 ").append(bySystem.size()).append(" 个\n");
        if (!byLevel.isEmpty()) {
            md.append("\n## 级别分布\n\n| 级别 | 数量 |\n| --- | --- |\n");
            byLevel.forEach((k, v) -> md.append("| ").append(k).append(" | ").append(v).append(" |\n"));
        }
        if (!bySystem.isEmpty()) {
            md.append("\n## 系统分布（Top 10）\n\n| 系统 | 数量 |\n| --- | --- |\n");
            bySystem.entrySet().stream()
                    .sorted((a, b) -> b.getValue() - a.getValue())
                    .limit(10)
                    .forEach(e -> md.append("| ").append(e.getKey()).append(" | ")
                            .append(e.getValue()).append(" |\n"));
        }
        if (!breaches.isEmpty()) {
            md.append("\n## 业务指标越限\n\n| 指标 | 值 | 时间 |\n| --- | --- | --- |\n");
            for (BizMetricSample s : breaches) {
                md.append("| ").append(s.getMetricCode()).append(" | ").append(s.getMetricValue())
                        .append(" | ").append(s.getSampleTime()).append(" |\n");
            }
        }
        md.append("\n> 本报表由平台本地数据（固化记录/处理流水/指标采样）聚合生成。\n");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("format", "md");
        data.put("report", md.toString());
        return ok("report.generate", data, null, startTime);
    }

    // ---------- nacos ----------

    private ToolResponse nacos(long startTime, Supplier<Map<String, Object>> call) {
        Map<String, Object> result = call.get();
        if (!Boolean.TRUE.equals(result.get("success"))) {
            return error("nacos", String.valueOf(result.getOrDefault("errorCode", "NACOS_CALL_FAILED")),
                    String.valueOf(result.getOrDefault("message", "Nacos 调用失败")), startTime);
        }
        return ok("nacos", result, null, startTime);
    }

    // ---------- 通用 ----------

    private List<ToolResponse.Citation> logCitations(List<AlertRecord> records) {
        return records.stream().limit(20).map(r -> {
            ToolResponse.Citation c = new ToolResponse.Citation();
            c.setKind("log");
            c.setSource("ai_alert_record");
            c.setTitle("固化日志 #" + r.getId());
            c.setLocator(String.valueOf(r.getId()));
            c.setSnippet(r.getContent() == null ? null
                    : r.getContent().length() > 120 ? r.getContent().substring(0, 120) : r.getContent());
            return c;
        }).toList();
    }

    private List<ToolResponse.Citation> alertCitations(List<AlertRecord> records) {
        List<ToolResponse.Citation> citations = logCitations(records);
        citations.forEach(c -> c.setKind("alert"));
        return citations;
    }

    private static void note(Map<String, Object> data, String text) {
        @SuppressWarnings("unchecked")
        List<String> notes = (List<String>) data.computeIfAbsent("notes", k -> new ArrayList<String>());
        notes.add(text);
    }

    private ToolResponse ok(String toolCode, Map<String, Object> data,
                            List<ToolResponse.Citation> citations, long startTime) {
        ToolResponse response = new ToolResponse();
        response.setSuccess(true);
        response.setData(data);
        if (citations != null && !citations.isEmpty()) {
            response.setCitations(citations);
        }
        response.setDurationMs(System.currentTimeMillis() - startTime);
        return response;
    }

    private ToolResponse error(String toolCode, String errorCode, String message, long startTime) {
        ToolResponse response = new ToolResponse();
        response.setSuccess(false);
        response.setErrorCode(errorCode);
        response.setErrorMessage(message);
        response.setDurationMs(System.currentTimeMillis() - startTime);
        return response;
    }

    private static String str(Map<String, Object> args, String key) {
        Object v = args.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private static String required(Map<String, Object> args, String key) {
        String v = str(args, key);
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("缺少参数 " + key);
        }
        return v;
    }

    private static Integer intOrNull(Map<String, Object> args, String key) {
        Object v = args.get(key);
        if (v instanceof Number num) {
            return num.intValue();
        }
        String s = str(args, key);
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("参数 " + key + " 应为整数: " + s);
        }
    }

    private static int intArg(Map<String, Object> args, String key, int defaultValue, int max) {
        Integer n = intOrNull(args, key);
        return Math.max(1, Math.min(n == null ? defaultValue : n, max));
    }

    private static long idArg(Map<String, Object> args, String key) {
        String s = required(args, key);
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("参数 " + key + " 应为数字ID: " + s);
        }
    }

    private static LocalDateTime time(Map<String, Object> args, String key, boolean requiredField) {
        String s = str(args, key);
        if (s == null || s.isBlank()) {
            if (requiredField) {
                throw new IllegalArgumentException("缺少参数 " + key);
            }
            return null;
        }
        String v = s.trim();
        for (DateTimeFormatter fmt : TIME_FORMATS) {
            try {
                java.time.temporal.TemporalAccessor parsed =
                        fmt.parseBest(v, LocalDateTime::from, LocalDate::from);
                if (parsed instanceof LocalDateTime dt) {
                    return dt;
                }
                if (parsed instanceof LocalDate d) {
                    return d.atStartOfDay();
                }
            } catch (Exception ignored) {
                // 逐个格式重试
            }
        }
        throw new IllegalArgumentException("时间格式非法（ISO-8601 或 yyyy-MM-dd [HH:mm[:ss]]）: " + s);
    }
}
