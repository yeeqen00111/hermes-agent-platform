package com.hermes.agent.api;

import com.hermes.agent.entity.OpsExperience;
import com.hermes.agent.service.OpsEvolutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 运维智能体「自我进化」API（白板·Agent 层·运维智能体）：
 * 经验记录 / 检索复用 / 反馈 / 定时巩固。
 */
@RestController
@RequestMapping("/api/ops/evolution")
@RequiredArgsConstructor
public class OpsEvolutionController {

    private final OpsEvolutionService service;

    @GetMapping("/experiences")
    public List<OpsExperience> list(@RequestParam(required = false) String agentCode,
                                    @RequestParam(required = false) String keyword,
                                    @RequestParam(required = false) String status) {
        return service.list(agentCode, keyword, status);
    }

    /** 沉淀一条「问题 → 方案」经验（同类问题自动归并强化） */
    @PostMapping("/experiences")
    public OpsExperience capture(@RequestBody OpsExperience experience) {
        return service.capture(experience);
    }

    /** 按问题检索可复用经验（计入复用次数） */
    @GetMapping("/retrieve")
    public List<Map<String, Object>> retrieve(@RequestParam String q,
                                              @RequestParam(required = false) String agentCode,
                                              @RequestParam(required = false, defaultValue = "3") int topK) {
        return service.retrieve(q, agentCode, topK);
    }

    /** 反馈：有效 / 无效（重算置信度） */
    @PostMapping("/experiences/{id}/feedback")
    public Map<String, Object> feedback(@PathVariable Long id, @RequestParam boolean useful) {
        OpsExperience exp = service.feedback(id, useful);
        return exp == null ? Map.of("success", false, "message", "经验不存在")
                : Map.of("success", true, "confidence", exp.getConfidence(), "status", exp.getStatus());
    }

    /** 手动触发巩固（定时任务同源） */
    @PostMapping("/evolve")
    public Map<String, Object> evolve() {
        return service.evolve();
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return service.stats();
    }
}
