package com.hermes.agent.ops;

import com.hermes.agent.service.OpsEvolutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 运维智能体「自我进化」巩固任务（白板·Agent 层·运维智能体）：
 * 每日对久未复用的经验衰减置信度、淘汰低置信度经验，使经验库随运维实践自我迭代。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpsEvolutionJob {

    private final OpsEvolutionService opsEvolutionService;

    @Scheduled(cron = "${hermes.ops.evolution-cron:0 15 4 * * ?}")
    public void consolidate() {
        try {
            opsEvolutionService.evolve();
        } catch (Exception e) {
            log.warn("自进化巩固任务失败: {}", e.getMessage());
        }
    }
}
