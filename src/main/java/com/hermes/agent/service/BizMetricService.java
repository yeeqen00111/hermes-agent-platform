package com.hermes.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hermes.agent.entity.AlertRecord;
import com.hermes.agent.entity.BizMetric;
import com.hermes.agent.entity.BizMetricSample;
import com.hermes.agent.mapper.AlertRecordMapper;
import com.hermes.agent.mapper.BizMetricMapper;
import com.hermes.agent.mapper.BizMetricSampleMapper;
import com.hermes.agent.notify.NotificationGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 业务指标监控（白板·业务层·智能运维）：
 * 指标定义（埋点 / 所属系统 / 指标类型）+ 采样 + 阈值判定；命中阈值落告警记录并经通知网关发送。
 * 采样入口 dev 由 {@code /api/ops/metric/ingest} 注入，生产由数据面（埋点/SQL/接口）推送同一入口。
 */
@Service
@RequiredArgsConstructor
public class BizMetricService {

    private final BizMetricMapper metricMapper;
    private final BizMetricSampleMapper sampleMapper;
    private final AlertRecordMapper alertRecordMapper;
    private final NotificationGateway notificationGateway;

    // ---------- 指标定义 ----------

    public List<BizMetric> listMetrics(String systemName) {
        LambdaQueryWrapper<BizMetric> wrapper = new LambdaQueryWrapper<BizMetric>()
                .orderByAsc(BizMetric::getId);
        if (systemName != null && !systemName.isBlank()) {
            wrapper.eq(BizMetric::getSystemName, systemName);
        }
        return metricMapper.selectList(wrapper);
    }

    public BizMetric saveMetric(BizMetric metric) {
        if (metric.getEnabled() == null) {
            metric.setEnabled(1);
        }
        if (metric.getId() != null) {
            metricMapper.updateById(metric);
        } else {
            metricMapper.insert(metric);
        }
        return metric;
    }

    public void deleteMetric(Long id) {
        metricMapper.deleteById(id);
    }

    // ---------- 采样 ----------

    public List<BizMetricSample> listSamples(String metricCode, int limit) {
        LambdaQueryWrapper<BizMetricSample> wrapper = new LambdaQueryWrapper<BizMetricSample>()
                .orderByDesc(BizMetricSample::getId);
        if (metricCode != null && !metricCode.isBlank()) {
            wrapper.eq(BizMetricSample::getMetricCode, metricCode);
        }
        wrapper.last("LIMIT " + Math.max(1, Math.min(limit, 500)));
        return sampleMapper.selectList(wrapper);
    }

    /**
     * 采样入库并判定阈值；命中则落告警记录 + 通知。
     */
    public Map<String, Object> ingest(String code, Double value, LocalDateTime sampleTime) {
        BizMetric metric = metricMapper.selectOne(new LambdaQueryWrapper<BizMetric>()
                .eq(BizMetric::getCode, code));
        if (metric == null) {
            return Map.of("success", false, "message", "指标不存在: " + code);
        }
        LocalDateTime time = sampleTime == null ? LocalDateTime.now() : sampleTime;
        boolean breached = breached(metric, value);

        BizMetricSample sample = new BizMetricSample();
        sample.setMetricCode(code);
        sample.setMetricValue(value);
        sample.setBreached(breached ? 1 : 0);
        sample.setSampleTime(time);
        sample.setCreateTime(LocalDateTime.now());
        sampleMapper.insert(sample);

        Long recordId = null;
        String status = null;
        if (breached) {
            status = "NO_CHANNEL";
            if (metric.getChannelCode() != null && !metric.getChannelCode().isBlank()) {
                boolean ok = notificationGateway.sendByCode(metric.getChannelCode(),
                        metric.getRecipient() == null ? "" : metric.getRecipient(),
                        "[HERMES指标告警] " + metric.getName(),
                        buildContent(metric, value));
                status = ok ? "SENT" : "FAILED";
            }
            AlertRecord record = new AlertRecord();
            record.setRuleCode(metric.getCode());
            record.setProjectName(metric.getSystemName());
            record.setSystemName(metric.getSystemName());
            record.setContent(buildContent(metric, value));
            record.setLogTime(time);
            record.setAlertType("METRIC");
            record.setStatus(status);
            record.setChannelCode(metric.getChannelCode());
            record.setRecipient(metric.getRecipient());
            record.setCreateTime(LocalDateTime.now());
            alertRecordMapper.insert(record);
            recordId = record.getId();
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        out.put("metricCode", code);
        out.put("value", value);
        out.put("threshold", metric.getThresholdOp() == null ? null
                : metric.getThresholdOp() + " " + metric.getThresholdValue());
        out.put("breached", breached);
        out.put("status", status);
        out.put("recordId", recordId);
        return out;
    }

    /**
     * 监控视图：每个指标 + 最新采样 + 是否命中阈值。
     */
    public List<Map<String, Object>> monitor() {
        List<BizMetric> metrics = listMetrics(null);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (BizMetric m : metrics) {
            BizMetricSample latest = sampleMapper.selectOne(new LambdaQueryWrapper<BizMetricSample>()
                    .eq(BizMetricSample::getMetricCode, m.getCode())
                    .orderByDesc(BizMetricSample::getId)
                    .last("LIMIT 1"));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", m.getId());
            row.put("code", m.getCode());
            row.put("name", m.getName());
            row.put("metricPoint", m.getMetricPoint());
            row.put("systemName", m.getSystemName());
            row.put("metricType", m.getMetricType());
            row.put("unit", m.getUnit());
            row.put("thresholdOp", m.getThresholdOp());
            row.put("thresholdValue", m.getThresholdValue());
            row.put("enabled", m.getEnabled());
            row.put("latestValue", latest == null ? null : latest.getMetricValue());
            row.put("latestTime", latest == null ? null : latest.getSampleTime());
            row.put("breached", latest != null && Integer.valueOf(1).equals(latest.getBreached()));
            rows.add(row);
        }
        return rows;
    }

    private boolean breached(BizMetric metric, Double value) {
        if (value == null || metric.getThresholdValue() == null) {
            return false;
        }
        String op = metric.getThresholdOp() == null ? ">" : metric.getThresholdOp().trim();
        double v = value;
        double t = metric.getThresholdValue();
        return switch (op) {
            case ">=" -> v >= t;
            case "<" -> v < t;
            case "<=" -> v <= t;
            case "==" -> v == t;
            default -> v > t;
        };
    }

    private String buildContent(BizMetric metric, Double value) {
        return "指标: " + metric.getName() + " (" + metric.getCode() + ")"
                + "\n所属系统: " + metric.getSystemName()
                + "\n指标类型: " + metric.getMetricType()
                + "\n当前值: " + value + (metric.getUnit() == null ? "" : metric.getUnit())
                + "\n阈值: " + metric.getThresholdOp() + " " + metric.getThresholdValue();
    }
}
