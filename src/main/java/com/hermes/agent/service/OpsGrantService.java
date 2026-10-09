package com.hermes.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hermes.agent.entity.OpsGrant;
import com.hermes.agent.mapper.OpsGrantMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 【AI】智能运维授权（白板·业务层·智能运维）：
 * 维护「运维助手可见哪些代码仓库 / nacos 配置 / 日志」，并可导出为 X-Data-Scope（interface-contract §3.6），
 * 交由工具 Guardrail 做数据范围校验（fail-open：未注入则放行，见 ADR-013）。
 */
@Service
@RequiredArgsConstructor
public class OpsGrantService {

    private final OpsGrantMapper grantMapper;

    public List<OpsGrant> listGrants(String agentCode, String grantType) {
        LambdaQueryWrapper<OpsGrant> wrapper = new LambdaQueryWrapper<OpsGrant>()
                .orderByAsc(OpsGrant::getId);
        if (agentCode != null && !agentCode.isBlank()) {
            wrapper.eq(OpsGrant::getAgentCode, agentCode);
        }
        if (grantType != null && !grantType.isBlank()) {
            wrapper.eq(OpsGrant::getGrantType, grantType.toUpperCase());
        }
        return grantMapper.selectList(wrapper);
    }

    public OpsGrant saveGrant(OpsGrant grant) {
        if (grant.getGrantType() != null) {
            grant.setGrantType(grant.getGrantType().toUpperCase());
        }
        if (grant.getPermission() == null || grant.getPermission().isBlank()) {
            grant.setPermission("READ");
        }
        if (grant.getEnabled() == null) {
            grant.setEnabled(1);
        }
        if (grant.getId() != null) {
            grantMapper.updateById(grant);
        } else {
            grantMapper.insert(grant);
        }
        return grant;
    }

    public void deleteGrant(Long id) {
        grantMapper.deleteById(id);
    }

    /**
     * 运维助手可见范围汇总：{repo:[...], nacos:[...], log:[...]}。
     */
    public Map<String, Object> scopeOf(String agentCode) {
        List<OpsGrant> grants = listGrants(agentCode, null);
        List<String> repo = new ArrayList<>();
        List<String> nacos = new ArrayList<>();
        List<String> log = new ArrayList<>();
        for (OpsGrant g : grants) {
            if (g.getEnabled() == null || g.getEnabled() != 1) {
                continue;
            }
            String ref = g.getResourceRef();
            if (ref == null || ref.isBlank()) {
                continue;
            }
            switch (g.getGrantType() == null ? "" : g.getGrantType().toUpperCase()) {
                case "REPO" -> repo.add(ref);
                case "NACOS" -> nacos.add(ref);
                case "LOG" -> log.add(ref);
                default -> { }
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("agentCode", agentCode);
        out.put("repo", repo);
        out.put("nacos", nacos);
        out.put("log", log);
        out.put("grants", grants);
        return out;
    }

    /**
     * 导出为 X-Data-Scope（维度：repo / nacos / log），供运维助手调用工具时携带。
     */
    public Map<String, List<String>> asDataScope(String agentCode) {
        List<OpsGrant> grants = listGrants(agentCode, null);
        Map<String, List<String>> scope = new LinkedHashMap<>();
        for (OpsGrant g : grants) {
            if (g.getEnabled() == null || g.getEnabled() != 1 || g.getResourceRef() == null
                    || g.getResourceRef().isBlank()) {
                continue;
            }
            String dimension = switch (g.getGrantType() == null ? "" : g.getGrantType().toUpperCase()) {
                case "REPO" -> "repo";
                case "NACOS" -> "nacos";
                case "LOG" -> "log";
                default -> null;
            };
            if (dimension != null) {
                scope.computeIfAbsent(dimension, k -> new ArrayList<>()).add(g.getResourceRef());
            }
        }
        return scope;
    }
}
