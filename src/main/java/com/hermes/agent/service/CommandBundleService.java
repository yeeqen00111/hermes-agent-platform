package com.hermes.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hermes.agent.entity.AiCommandBundle;
import com.hermes.agent.mapper.AiCommandBundleMapper;
import com.hermes.agent.skill.SkillRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 指令捆绑包管理（白板·Agent 层·补充项「指令」）：
 * 一条指令预载一串技能（§7.4）。此前仅经对话 /{bundleCode} 生效，无管理端点，此处补齐 CRUD 与拼装预览。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommandBundleService {

    private final AiCommandBundleMapper bundleMapper;
    private final SkillRegistry skillRegistry;
    private final ObjectMapper objectMapper;

    public List<AiCommandBundle> list() {
        return bundleMapper.selectList(new LambdaQueryWrapper<AiCommandBundle>()
                .orderByAsc(AiCommandBundle::getBundleCode));
    }

    public AiCommandBundle save(AiCommandBundle bundle) {
        if (bundle.getStatus() == null || bundle.getStatus().isBlank()) {
            bundle.setStatus("ENABLED");
        }
        if (bundle.getVersion() == null) {
            bundle.setVersion(1);
        }
        if (bundle.getId() != null) {
            bundleMapper.updateById(bundle);
        } else {
            bundleMapper.insert(bundle);
        }
        return bundle;
    }

    public void delete(Long id) {
        bundleMapper.deleteById(id);
    }

    /**
     * 拼装预览：按技能清单取当前版本正文拼接（与 CommandRouter 命中时的行为一致），
     * 并回报未启用/无正文的技能编码。
     */
    public Map<String, Object> preview(Long id) {
        AiCommandBundle bundle = bundleMapper.selectById(id);
        if (bundle == null) {
            return Map.of("success", false, "message", "捆绑包不存在");
        }
        List<String> codes = parseCodes(bundle.getSkillCodes());
        List<Map<String, Object>> skills = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        List<String> parts = new ArrayList<>();
        for (String code : codes) {
            Optional<String> content = skillRegistry.content(code);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("code", code);
            row.put("loaded", content.isPresent());
            skills.add(row);
            if (content.isPresent()) {
                parts.add(content.get());
            } else {
                missing.add(code);
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        out.put("bundleCode", bundle.getBundleCode());
        out.put("command", "/" + bundle.getBundleCode());
        out.put("skills", skills);
        out.put("missing", missing);
        out.put("content", parts.isEmpty() ? "（无可用技能正文，命中时该指令将无法预载内容）"
                : String.join("\n\n---\n\n", parts));
        return out;
    }

    private List<String> parseCodes(String skillCodes) {
        List<String> codes = new ArrayList<>();
        if (skillCodes == null || skillCodes.isBlank()) {
            return codes;
        }
        try {
            JsonNode node = objectMapper.readTree(skillCodes);
            if (node.isArray()) {
                node.forEach(n -> {
                    if (!n.asText().isBlank()) {
                        codes.add(n.asText());
                    }
                });
            }
        } catch (Exception e) {
            log.warn("捆绑包技能清单解析失败: {}", skillCodes);
        }
        return codes;
    }
}
