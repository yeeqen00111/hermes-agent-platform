package com.hermes.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hermes.agent.entity.AgentProfile;
import com.hermes.agent.entity.AiModel;
import com.hermes.agent.entity.ModelProvider;
import com.hermes.agent.mapper.AgentProfileMapper;
import com.hermes.agent.mapper.AiModelMapper;
import com.hermes.agent.mapper.ModelProviderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 大模型型号映射核验（白板：大模型 DeepSeek Pro / DeepSeek Flash / GLM）。
 *
 * <p>离线核验（不调用供应商接口）：① 模型→供应商存在且启用；② 已发布 Agent 引用的模型在目录中、
 * 启用且支持工具调用（契约要求 AI 回答必须用支持工具调用的模型）；③ 档位（tier）标注完整、PRO/FLASH 均有启用模型。
 */
@Service
@RequiredArgsConstructor
public class ModelCatalogService {

    /** 白板口径的档位 */
    private static final List<String> REQUIRED_TIERS = List.of("PRO", "FLASH");

    private final AiModelMapper modelMapper;
    private final ModelProviderMapper providerMapper;
    private final AgentProfileMapper profileMapper;

    public Map<String, Object> verify() {
        List<String> issues = new ArrayList<>();

        List<ModelProvider> providers = providerMapper.selectList(new LambdaQueryWrapper<>());
        Set<String> enabledProviders = new LinkedHashSet<>();
        providers.stream().filter(p -> Integer.valueOf(1).equals(p.getEnabled()))
                .forEach(p -> enabledProviders.add(p.getProviderCode()));

        List<AiModel> models = modelMapper.selectList(new LambdaQueryWrapper<>());
        Set<String> enabledProviderModels = new LinkedHashSet<>();
        Map<String, Map<String, Object>> byTier = new LinkedHashMap<>();
        for (AiModel m : models) {
            boolean enabled = Integer.valueOf(1).equals(m.getEnabled());
            if (enabled) {
                enabledProviderModels.add(key(m.getProviderCode(), m.getModelName()));
            }
            if (!enabled) {
                continue;
            }
            if (!enabledProviders.contains(m.getProviderCode())) {
                issues.add("模型 " + key(m.getProviderCode(), m.getModelName()) + " 的供应商不存在或未启用");
            }
            if (m.getTier() == null || m.getTier().isBlank()) {
                issues.add("模型 " + key(m.getProviderCode(), m.getModelName()) + " 未标注档位（tier）");
            } else {
                Map<String, Object> mapping = new LinkedHashMap<>();
                mapping.put("tier", m.getTier());
                mapping.put("providerCode", m.getProviderCode());
                mapping.put("modelName", m.getModelName());
                mapping.put("displayName", m.getDisplayName());
                mapping.put("contextWindow", m.getContextWindow());
                mapping.put("supportsTools", m.getSupportsTools());
                byTier.put(m.getTier().toUpperCase() + "|" + m.getProviderCode() + "/" + m.getModelName(), mapping);
            }
        }

        for (String tier : REQUIRED_TIERS) {
            boolean has = models.stream().anyMatch(m -> Integer.valueOf(1).equals(m.getEnabled())
                    && tier.equalsIgnoreCase(m.getTier()));
            if (!has) {
                issues.add("档位 " + tier + " 无启用模型（白板要求 DeepSeek Pro / DeepSeek Flash 均可用）");
            }
        }

        List<AgentProfile> profiles = profileMapper.selectList(new LambdaQueryWrapper<AgentProfile>()
                .eq(AgentProfile::getStatus, "PUBLISHED"));
        for (AgentProfile p : profiles) {
            if (p.getModelProvider() == null || p.getModelProvider().isBlank()
                    || p.getModelName() == null || p.getModelName().isBlank()) {
                issues.add("Agent " + p.getAgentCode() + " 未配置模型");
                continue;
            }
            String k = key(p.getModelProvider(), p.getModelName());
            if (!enabledProviderModels.contains(k)) {
                issues.add("Agent " + p.getAgentCode() + " 引用的模型不在目录或未启用: " + k);
                continue;
            }
            AiModel hit = models.stream()
                    .filter(m -> key(m.getProviderCode(), m.getModelName()).equals(k)
                            && Integer.valueOf(1).equals(m.getEnabled()))
                    .findFirst().orElse(null);
            if (hit != null && !Integer.valueOf(1).equals(hit.getSupportsTools())) {
                issues.add("Agent " + p.getAgentCode() + " 引用的模型不支持工具调用: " + k
                        + "（契约要求 AI 回答必须用支持工具调用的模型）");
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", issues.isEmpty());
        out.put("issues", issues);
        out.put("tiers", new ArrayList<>(byTier.values()));
        out.put("providerCount", providers.size());
        out.put("modelCount", models.size());
        out.put("agentCount", profiles.size());
        return out;
    }

    private static String key(String provider, String model) {
        return provider + "/" + model;
    }
}
