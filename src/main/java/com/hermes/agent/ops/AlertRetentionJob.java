package com.hermes.agent.ops;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hermes.agent.entity.AlertRecord;
import com.hermes.agent.entity.LogRetention;
import com.hermes.agent.mapper.AlertRecordMapper;
import com.hermes.agent.mapper.LogRetentionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 日志清理任务（白板·智能运维·日志分级保留）：
 * 按策略清理告警/提级告警记录（全量日志保留 3 天 / 告警 7 天 / 提级告警 7 天）。
 * 全量日志（FULL）不落本表，由日志采集侧承载，此处仅执行告警类清理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertRetentionJob {

    private final LogRetentionMapper retentionMapper;
    private final AlertRecordMapper alertRecordMapper;

    @Scheduled(cron = "${hermes.ops.retention-cron:0 30 3 * * ?}")
    public void scheduled() {
        clean();
    }

    /** 执行清理，返回各类型删除条数（供手动触发） */
    @Transactional
    public Map<String, Integer> clean() {
        List<LogRetention> policies = retentionMapper.selectList(new LambdaQueryWrapper<LogRetention>()
                .orderByAsc(LogRetention::getId));
        Map<String, Integer> deleted = new LinkedHashMap<>();
        for (LogRetention policy : policies) {
            if (policy.getEnabled() == null || policy.getEnabled() != 1) {
                continue;
            }
            String type = policy.getDataType();
            if (!"ALERT".equals(type) && !"PROMOTE".equals(type)) {
                continue; // FULL 不在本表
            }
            int days = policy.getRetentionDays() == null ? 7 : Math.max(1, policy.getRetentionDays());
            LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
            int rows = alertRecordMapper.delete(new LambdaQueryWrapper<AlertRecord>()
                    .eq(AlertRecord::getAlertType, type)
                    .lt(AlertRecord::getCreateTime, cutoff));
            deleted.put(type, rows);
        }
        if (!deleted.isEmpty()) {
            log.info("日志清理完成: {}", deleted);
        }
        return deleted;
    }
}
