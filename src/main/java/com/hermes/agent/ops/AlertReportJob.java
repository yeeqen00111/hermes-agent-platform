package com.hermes.agent.ops;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hermes.agent.entity.AlertReport;
import com.hermes.agent.mapper.AlertReportMapper;
import com.hermes.agent.service.AlertReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 【AI】智能告警报表定时任务：每 60s 检查一次，按各报表「发送频率」到期即生成并发送。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertReportJob {

    private final AlertReportMapper reportMapper;
    private final AlertReportService alertReportService;

    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void tick() {
        LocalDateTime now = LocalDateTime.now();
        List<AlertReport> reports;
        try {
            reports = reportMapper.selectList(new LambdaQueryWrapper<AlertReport>()
                    .eq(AlertReport::getEnabled, 1));
        } catch (Exception e) {
            // 首次任务可能与建表竞态，跳过本轮
            log.debug("告警报表任务读取失败（可能建表未完成），本轮跳过: {}", e.getMessage());
            return;
        }
        for (AlertReport report : reports) {
            int hours = report.getFrequencyHours() == null ? 2 : Math.max(1, report.getFrequencyHours());
            boolean due = report.getLastSendTime() == null
                    || !now.isBefore(report.getLastSendTime().plusHours(hours));
            if (!due) {
                continue;
            }
            try {
                alertReportService.run(report.getCode(), null);
            } catch (Exception e) {
                log.warn("告警报表定时发送失败: code={}, err={}", report.getCode(), e.getMessage());
            }
        }
    }
}
