package com.hermes.agent.api;

import com.hermes.agent.entity.AgentProfile;
import com.hermes.agent.service.AgentProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Agent 管理 API
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AgentController {

    private final AgentProfileService agentProfileService;

    /**
     * 列出所有Agent
     */
    @GetMapping("/agent-profiles")
    public List<AgentProfile> listAgents() {
        return agentProfileService.listEnabled();
    }

    /**
     * 获取单个Agent
     */
    @GetMapping("/agent-profiles/{code}")
    public Map<String, Object> getAgent(@PathVariable String code) {
        return agentProfileService.getByCode(code)
                .map(agent -> Map.of("success", true, "data", agent))
                .orElse(Map.of("success", false, "message", "Agent not found"));
    }

    /**
     * 创建Agent
     */
    @PostMapping("/agent-profiles")
    public Map<String, Object> createAgent(@RequestBody AgentProfile profile) {
        agentProfileService.saveOrUpdate(profile);
        return Map.of("success", true, "message", "Agent created");
    }

    /**
     * 更新Agent
     */
    @PutMapping("/agent-profiles/{id}")
    public Map<String, Object> updateAgent(@PathVariable Long id, @RequestBody AgentProfile profile) {
        profile.setId(id);
        agentProfileService.saveOrUpdate(profile);
        return Map.of("success", true, "message", "Agent updated");
    }

    /**
     * 发布Agent
     */
    @PostMapping("/agent-profiles/{id}/publish")
    public Map<String, Object> publishAgent(@PathVariable Long id) {
        agentProfileService.publish(id);
        return Map.of("success", true, "message", "Agent published");
    }
}
