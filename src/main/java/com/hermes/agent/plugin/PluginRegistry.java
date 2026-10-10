package com.hermes.agent.plugin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hermes.agent.entity.AiAgentPlugin;
import com.hermes.agent.entity.AiPlugin;
import com.hermes.agent.mapper.AiAgentPluginMapper;
import com.hermes.agent.mapper.AiPluginMapper;
import com.hermes.agent.skill.SkillRegistry;
import com.hermes.agent.tool.ToolRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 插件注册中心（补充项「插件」）。
 *
 * <p>口径：插件是**自有进程内注册的能力包**，不是外部 ABI——{@code agent-platform.md} 已定调
 * 「我们没有宿主，钩子自动消失；工具调用、审批、参数注入全在自己进程里」。
 * 因此插件不引入第二套扩展体系，它只做一件事：把「一组工具 + 技能 + 指令 + 提示词」
 * 打包成一个可注册、可停用、可绑定到智能体的单元。
 *
 * <p>装载时机：{@code @PostConstruct} 拉取启用中的插件进内存索引，
 * 增删改/启停后由管理端 {@code reload()} 重建（与 {@code SkillRegistry} 同一套做法）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PluginRegistry {

    private final AiPluginMapper pluginMapper;
    private final AiAgentPluginMapper bindingMapper;
    private final ToolRegistry toolRegistry;
    private final SkillRegistry skillRegistry;
    private final ObjectMapper objectMapper;

    /** 已装载插件：编码 → 解析后的能力清单 */
    private volatile Map<String, Plugin> loaded = Map.of();
    /** 绑定关系：智能体 → 启用中的插件编码集合 */
    private volatile Map<String, Set<String>> agentBindings = Map.of();

    /** 插件能力清单（解析后） */
    public record Plugin(String pluginCode,
                         String name,
                         String version,
                         String pluginType,
                         String description,
                         Set<String> tools,
                         Set<String> skills,
                         Set<String> commands,
                         String prompt,
                         String config) {
    }

    /**
     * 启动时装载（表尚未建好时静默跳过：{@code DatabaseInitializer} 在本 Bean 之后跑）。
     */
    @jakarta.annotation.PostConstruct
    public void init() {
        try {
            reload();
        } catch (Exception e) {
            log.warn("插件装载跳过（数据表可能尚未就绪）: {}", e.getMessage());
        }
    }

    /**
     * 重建装载索引：仅装载 {@code enabled=1 且 status=ACTIVE} 的插件，
     * 并按绑定表算出「每个智能体实际生效的插件」。
     */
    public synchronized void reload() {
        List<AiPlugin> active = pluginMapper.selectList(new LambdaQueryWrapper<AiPlugin>()
                .eq(AiPlugin::getEnabled, 1)
                .eq(AiPlugin::getStatus, "ACTIVE")
                .orderByAsc(AiPlugin::getPluginCode));

        Map<String, Plugin> plugins = new LinkedHashMap<>();
        for (AiPlugin row : active) {
            Plugin parsed = parse(row);
            if (parsed != null) {
                plugins.put(parsed.pluginCode(), parsed);
            }
        }

        List<AiAgentPlugin> bindings = bindingMapper.selectList(new LambdaQueryWrapper<AiAgentPlugin>()
                .eq(AiAgentPlugin::getEnabled, 1));

        Map<String, Set<String>> byAgent = new LinkedHashMap<>();
        for (AiAgentPlugin b : bindings) {
            // 插件已停用/删除时绑定自动失效（不再注入），并留日志便于排查
            if (!plugins.containsKey(b.getPluginCode())) {
                log.warn("绑定指向未启用的插件，运行时忽略: agent={} plugin={}",
                        b.getAgentCode(), b.getPluginCode());
                continue;
            }
            byAgent.computeIfAbsent(b.getAgentCode(), k -> new LinkedHashSet<>()).add(b.getPluginCode());
        }

        this.loaded = Map.copyOf(plugins);
        this.agentBindings = Map.copyOf(byAgent);
        log.info("插件索引已装载: {} 个启用插件, {} 个智能体有绑定", plugins.size(), byAgent.size());
    }

    /** 全部启用中的插件（按编码排序） */
    public List<Plugin> loadedPlugins() {
        return new ArrayList<>(loaded.values());
    }

    /** 某智能体实际生效的插件（绑定 ∩ 启用） */
    public List<Plugin> pluginsForAgent(String agentCode) {
        Set<String> codes = agentBindings.get(agentCode);
        if (codes == null || codes.isEmpty()) {
            return List.of();
        }
        return codes.stream().map(loaded::get).filter(java.util.Objects::nonNull).toList();
    }

    /**
     * 某智能体因插件而额外获得的工具编码（工具白名单的并集来源）。
     */
    public Set<String> toolCodesForAgent(String agentCode) {
        Set<String> out = new LinkedHashSet<>();
        for (Plugin p : pluginsForAgent(agentCode)) {
            out.addAll(p.tools());
        }
        return out;
    }

    /**
     * 某智能体因插件而额外获得的技能编码（技能索引的并集来源）。
     */
    public Set<String> skillCodesForAgent(String agentCode) {
        Set<String> out = new LinkedHashSet<>();
        for (Plugin p : pluginsForAgent(agentCode)) {
            out.addAll(p.skills());
        }
        return out;
    }

    /**
     * 某智能体因插件而额外获得的指令编码。
     */
    public Set<String> commandCodesForAgent(String agentCode) {
        Set<String> out = new LinkedHashSet<>();
        for (Plugin p : pluginsForAgent(agentCode)) {
            out.addAll(p.commands());
        }
        return out;
    }

    /**
     * 插件提示词块（进系统提示「## 插件能力」段）。无绑定插件时返回空列表。
     */
    public List<String> promptBlocks(String agentCode) {
        List<String> blocks = new ArrayList<>();
        for (Plugin p : pluginsForAgent(agentCode)) {
            if (p.prompt() != null && !p.prompt().isBlank()) {
                blocks.add("### " + p.name() + "（" + p.pluginCode() + " v" + p.version() + "）\n"
                        + p.prompt().trim());
            }
        }
        return blocks;
    }

    /**
     * 装载前一致性核验：清单里声明的工具/技能是否真的存在（防止启用一个空壳插件）。
     * 返回缺失项清单，空列表表示全部可装载。
     */
    public List<String> verify(String pluginCode) {
        AiPlugin row = pluginMapper.selectOne(new LambdaQueryWrapper<AiPlugin>()
                .eq(AiPlugin::getPluginCode, pluginCode));
        if (row == null) {
            return List.of("插件不存在: " + pluginCode);
        }
        Plugin p = parse(row);
        if (p == null) {
            return List.of("manifest 解析失败: " + pluginCode);
        }
        List<String> missing = new ArrayList<>();
        for (String tool : p.tools()) {
            if (!toolRegistry.isToolAvailable(tool)) {
                missing.add("工具未注册: " + tool);
            }
        }
        for (String skill : p.skills()) {
            if (skillRegistry.enabledSkills().stream()
                    .noneMatch(s -> skill.equals(s.getSkillCode()))) {
                missing.add("技能未启用: " + skill);
            }
        }
        return missing;
    }

    /** 解析 manifest/config；manifest 非法 JSON 视为不可装载（返回 null）。 */
    private Plugin parse(AiPlugin row) {
        try {
            JsonNode node = objectMapper.readTree(row.getManifest() == null || row.getManifest().isBlank()
                    ? "{}" : row.getManifest());
            return new Plugin(
                    row.getPluginCode(),
                    row.getName(),
                    row.getVersion() == null ? "1.0.0" : row.getVersion(),
                    row.getPluginType() == null ? "CAPABILITY" : row.getPluginType(),
                    row.getDescription(),
                    textSet(node.get("tools")),
                    textSet(node.get("skills")),
                    textSet(node.get("commands")),
                    node.path("prompt").asText(""),
                    row.getConfig() == null ? "" : row.getConfig());
        } catch (Exception e) {
            log.warn("插件 manifest 解析失败，跳过装载: {} ({})", row.getPluginCode(), e.getMessage());
            return null;
        }
    }

    private Set<String> textSet(JsonNode arr) {
        Set<String> out = new LinkedHashSet<>();
        if (arr != null && arr.isArray()) {
            arr.forEach(n -> {
                String v = n.asText("").trim();
                if (!v.isEmpty()) {
                    out.add(v);
                }
            });
        }
        return out;
    }
}
