package com.hermes.agent.api;

import com.hermes.agent.entity.AgentProfile;
import com.hermes.agent.persona.VersionService;
import com.hermes.agent.runtime.AgentRunRequest;
import com.hermes.agent.runtime.AgentRunResult;
import com.hermes.agent.runtime.AgentRuntime;
import com.hermes.agent.service.AgentProfileService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Agent 管理 API（契约 §3.5：配置权威在智能体平台）。
 * 发布/回滚/版本历史统一走 VersionService（快照+移指针，ADR-009），不再存在"只 +1 不建快照"的旁路。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AgentController {

    private final AgentProfileService agentProfileService;
    private final VersionService versionService;
    private final AgentRuntime agentRuntime;

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
     * 更新Agent（草稿）
     */
    @PutMapping("/agent-profiles/{id}")
    public Map<String, Object> updateAgent(@PathVariable Long id, @RequestBody AgentProfile profile) {
        profile.setId(id);
        agentProfileService.saveOrUpdate(profile);
        return Map.of("success", true, "message", "Agent updated");
    }

    /**
     * 发布Agent：写不可变快照 + 移版本指针（ADR-009）
     */
    @PostMapping("/agent-profiles/{id}/publish")
    public Map<String, Object> publishAgent(@PathVariable Long id,
                                            @RequestParam(required = false, defaultValue = "system") String by) {
        return agentProfileService.getById(id)
                .map(profile -> Map.<String, Object>of("success", true,
                        "version", versionService.publish(profile.getAgentCode(), by).getVersion()))
                .orElseGet(() -> Map.of("success", false, "message", "Agent not found"));
    }

    /**
     * 回滚到指定版本
     */
    @PostMapping("/agent-profiles/{id}/rollback")
    public Map<String, Object> rollbackAgent(@PathVariable Long id, @RequestParam int version) {
        return agentProfileService.getById(id)
                .map(profile -> {
                    AgentProfile rolled = versionService.rollback(profile.getAgentCode(), version);
                    return Map.<String, Object>of("success", true, "data", rolled);
                })
                .orElseGet(() -> Map.of("success", false, "message", "Agent not found"));
    }

    /**
     * 版本历史
     */
    @GetMapping("/agent-profiles/{id}/versions")
    public Map<String, Object> listVersions(@PathVariable Long id) {
        return agentProfileService.getById(id)
                .map(profile -> Map.<String, Object>of("success", true, "data", versionService.versions(profile.getAgentCode())))
                .orElseGet(() -> Map.of("success", false, "message", "Agent not found"));
    }

    /**
     * 灰度开关（ADR-009 / agent-platform §8.2）：新会话按 {@code ratio}% 指向 {@code version}；
     * {@code ratio<=0} 关闭灰度。已建会话不受影响（会话中途不切版本）。
     */
    @PostMapping("/agent-profiles/{id}/gray")
    public Map<String, Object> grayAgent(@PathVariable Long id, @RequestBody GrayRequest request) {
        return agentProfileService.getById(id)
                .map(profile -> {
                    try {
                        AgentProfile updated = versionService.setGray(profile.getAgentCode(),
                                request.getVersion(), request.getRatio() == null ? 0 : request.getRatio());
                        Map<String, Object> data = new LinkedHashMap<>();
                        data.put("success", true);
                        data.put("agentCode", updated.getAgentCode());
                        data.put("currentVersion", updated.getCurrentVersion());
                        data.put("grayVersion", updated.getGrayVersion());
                        data.put("grayRatio", updated.getGrayRatio());
                        return data;
                    } catch (IllegalArgumentException e) {
                        return Map.<String, Object>of("success", false, "message", e.getMessage());
                    }
                })
                .orElseGet(() -> Map.of("success", false, "message", "Agent not found"));
    }

    /**
     * 试跑（契约 §3.5：发布前必须验）——用当前草稿配置跑一轮真实问答。
     * 不建会话、不落聊天历史；无审批通道，WRITE/CONTROLLED 工具一律拒绝（fail-closed）；
     * 工具调用审计照记，channel=dry-run 便于与线上正式对话区分。
     */
    @PostMapping("/agent-profiles/{id}/dry-run")
    public Map<String, Object> dryRunAgent(@PathVariable Long id, @RequestBody DryRunRequest request) {
        if (request.getMessage() == null || request.getMessage().isBlank()) {
            return Map.of("success", false, "message", "message 不能为空");
        }
        return agentProfileService.getById(id)
                .map(profile -> {
                    AgentRunRequest runRequest = AgentRunRequest.builder()
                            .agentCode(profile.getAgentCode())
                            .userId(request.getUserId())
                            .userInput(request.getMessage())
                            .traceId(UUID.randomUUID().toString())
                            .channel("dry-run")
                            .build();
                    AgentRunResult result = agentRuntime.run(runRequest);
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("success", true);
                    data.put("agentCode", profile.getAgentCode());
                    data.put("executionMode", profile.getExecutionMode());
                    data.put("fixedFlow", result.isFixedFlow());
                    data.put("runId", result.getRunId());
                    data.put("reply", result.getReply() == null ? "" : result.getReply());
                    data.put("toolEvents", result.getToolEvents());
                    return data;
                })
                .orElseGet(() -> Map.of("success", false, "message", "Agent not found"));
    }

    @Data
    public static class DryRunRequest {
        private String message;
        private Long userId;
    }

    @Data
    public static class GrayRequest {
        /** 目标版本号；ratio<=0 时可空（表示关闭灰度） */
        private Integer version;
        /** 灰度比例 0-100 */
        private Integer ratio;
    }
}
