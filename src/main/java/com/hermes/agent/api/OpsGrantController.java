package com.hermes.agent.api;

import com.hermes.agent.entity.OpsGrant;
import com.hermes.agent.service.OpsGrantService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * 【AI】智能运维授权 API（白板·业务层·智能运维）：
 * 代码仓库授权 / nacos 配置授权 / 日志授权；并按运维助手汇总可见范围（可导出 X-Data-Scope）。
 */
@RestController
@RequestMapping("/api/ops/grant")
@RequiredArgsConstructor
public class OpsGrantController {

    private final OpsGrantService service;

    @GetMapping("/grants")
    public List<OpsGrant> listGrants(@RequestParam(required = false) String agentCode,
                                     @RequestParam(required = false) String type) {
        return service.listGrants(agentCode, type);
    }

    @PostMapping("/grants")
    public OpsGrant saveGrant(@RequestBody OpsGrant grant) {
        return service.saveGrant(grant);
    }

    @DeleteMapping("/grants/{id}")
    public Map<String, Object> deleteGrant(@PathVariable Long id) {
        service.deleteGrant(id);
        return Map.of("success", true);
    }

    /** 运维助手可见范围汇总 */
    @GetMapping("/scope")
    public Map<String, Object> scope(@RequestParam String agentCode) {
        return service.scopeOf(agentCode);
    }

    /** 导出 X-Data-Scope（供运维助手调用工具时携带，交 Guardrail 校验） */
    @GetMapping("/data-scope")
    public Map<String, List<String>> dataScope(@RequestParam String agentCode) {
        return service.asDataScope(agentCode);
    }
}
