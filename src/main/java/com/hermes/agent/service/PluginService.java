package com.hermes.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hermes.agent.entity.AgentProfile;
import com.hermes.agent.entity.AiAgentPlugin;
import com.hermes.agent.entity.AiPlugin;
import com.hermes.agent.mapper.AgentProfileMapper;
import com.hermes.agent.mapper.AiAgentPluginMapper;
import com.hermes.agent.mapper.AiPluginMapper;
import com.hermes.agent.plugin.PluginRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 插件管理（补充项「插件」）：注册、启停、与智能体绑定，以及装载一致性核验。
 *
 * <p>插件能力本身由 {@link PluginRegistry} 提供；此处是控制面。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PluginService {

    private final AiPluginMapper pluginMapper;
    private final AiAgentPluginMapper bindingMapper;
    private final AgentProfileMapper profileMapper;
    private final PluginRegistry registry;

    public List<AiPlugin> list() {
        return pluginMapper.selectList(new LambdaQueryWrapper<AiPlugin>()
                .orderByAsc(AiPlugin::getPluginCode));
    }

    /**
     * 注册或更新插件。更新后重建装载索引（启停即时生效）。
     */
    public AiPlugin save(AiPlugin plugin) {
        if (plugin.getStatus() == null || plugin.getStatus().isBlank()) {
            plugin.setStatus("ACTIVE");
        }
        if (plugin.getVersion() == null || plugin.getVersion().isBlank()) {
            plugin.setVersion("1.0.0");
        }
        if (plugin.getEnabled() == null) {
            plugin.setEnabled(1);
        }
        if (plugin.getId() != null) {
            pluginMapper.updateById(plugin);
        } else {
            pluginMapper.insert(plugin);
        }
        registry.reload();
        return pluginMapper.selectOne(new LambdaQueryWrapper<AiPlugin>()
                .eq(AiPlugin::getPluginCode, plugin.getPluginCode()));
    }

    public void delete(Long id) {
        AiPlugin plugin = pluginMapper.selectById(id);
        if (plugin != null) {
            // 清掉绑定，避免残留指向已删插件
            bindingMapper.delete(new LambdaQueryWrapper<AiAgentPlugin>()
                    .eq(AiAgentPlugin::getPluginCode, plugin.getPluginCode()));
        }
        pluginMapper.deleteById(id);
        registry.reload();
    }

    /** 启用/停用插件（停用即从运行时卸载，其绑定自动失效） */
    public AiPlugin setEnabled(Long id, boolean enabled) {
        AiPlugin plugin = pluginMapper.selectById(id);
        if (plugin == null) {
            throw new IllegalArgumentException("插件不存在: " + id);
        }
        plugin.setEnabled(enabled ? 1 : 0);
        plugin.setStatus(enabled ? "ACTIVE" : "DISABLED");
        pluginMapper.updateById(plugin);
        registry.reload();
        return plugin;
    }

    /**
     * 绑定插件到智能体（幂等：已存在则置为启用）。
     */
    public Map<String, Object> bind(String agentCode, String pluginCode) {
        AiPlugin plugin = requirePlugin(pluginCode);
        requireAgent(agentCode);
        AiAgentPlugin exist = bindingMapper.selectOne(new LambdaQueryWrapper<AiAgentPlugin>()
                .eq(AiAgentPlugin::getAgentCode, agentCode)
                .eq(AiAgentPlugin::getPluginCode, pluginCode));
        if (exist == null) {
            AiAgentPlugin b = new AiAgentPlugin();
            b.setAgentCode(agentCode);
            b.setPluginCode(pluginCode);
            b.setEnabled(1);
            bindingMapper.insert(b);
        } else if (!Integer.valueOf(1).equals(exist.getEnabled())) {
            exist.setEnabled(1);
            bindingMapper.updateById(exist);
        }
        registry.reload();
        return Map.of("success", true, "agentCode", agentCode, "pluginCode", pluginCode,
                "active", Integer.valueOf(1).equals(plugin.getEnabled()) && "ACTIVE".equals(plugin.getStatus()));
    }

    public Map<String, Object> unbind(String agentCode, String pluginCode) {
        bindingMapper.delete(new LambdaQueryWrapper<AiAgentPlugin>()
                .eq(AiAgentPlugin::getAgentCode, agentCode)
                .eq(AiAgentPlugin::getPluginCode, pluginCode));
        registry.reload();
        return Map.of("success", true);
    }

    /** 某智能体当前生效的插件（含绑定但插件已停用的，标注状态） */
    public List<Map<String, Object>> pluginsForAgent(String agentCode) {
        List<AiAgentPlugin> bindings = bindingMapper.selectList(new LambdaQueryWrapper<AiAgentPlugin>()
                .eq(AiAgentPlugin::getAgentCode, agentCode));
        List<Map<String, Object>> out = new ArrayList<>();
        for (AiAgentPlugin b : bindings) {
            AiPlugin p = pluginMapper.selectOne(new LambdaQueryWrapper<AiPlugin>()
                    .eq(AiPlugin::getPluginCode, b.getPluginCode()));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("agentCode", agentCode);
            row.put("pluginCode", b.getPluginCode());
            row.put("boundEnabled", b.getEnabled());
            row.put("pluginName", p == null ? null : p.getName());
            row.put("pluginEnabled", p == null ? null : p.getEnabled());
            row.put("pluginStatus", p == null ? "MISSING" : p.getStatus());
            out.add(row);
        }
        return out;
    }

    /**
     * 全量核验：① 每个启用插件的清单项是否可装载（工具已注册/技能已启用）；
     * ② 每个绑定是否指向存在的智能体与插件；③ 每个智能体的绑定插件是否都已启用（否则提示绑定失效）。
     */
    public Map<String, Object> verify() {
        List<String> issues = new ArrayList<>();
        List<Map<String, Object>> pluginRows = new ArrayList<>();

        for (AiPlugin p : list()) {
            boolean active = Integer.valueOf(1).equals(p.getEnabled()) && "ACTIVE".equals(p.getStatus());
            List<String> missing = active ? registry.verify(p.getPluginCode()) : List.of();
            if (active && !missing.isEmpty()) {
                issues.add("插件 " + p.getPluginCode() + " 清单存在不可装载项: " + String.join("; ", missing));
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("pluginCode", p.getPluginCode());
            row.put("name", p.getName());
            row.put("active", active);
            row.put("missing", missing);
            pluginRows.add(row);
        }

        List<AiAgentPlugin> bindings = bindingMapper.selectList(null);
        for (AiAgentPlugin b : bindings) {
            AiPlugin p = pluginMapper.selectOne(new LambdaQueryWrapper<AiPlugin>()
                    .eq(AiPlugin::getPluginCode, b.getPluginCode()));
            if (p == null) {
                issues.add("绑定指向不存在的插件: agent=" + b.getAgentCode() + " plugin=" + b.getPluginCode());
                continue;
            }
            requireAgent(b.getAgentCode());
            if (Integer.valueOf(1).equals(b.getEnabled())
                    && (!Integer.valueOf(1).equals(p.getEnabled()) || !"ACTIVE".equals(p.getStatus()))) {
                issues.add("绑定已失效（插件未启用）: agent=" + b.getAgentCode() + " plugin=" + b.getPluginCode());
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", issues.isEmpty());
        out.put("issues", issues);
        out.put("plugins", pluginRows);
        out.put("pluginCount", pluginRows.size());
        out.put("bindingCount", bindings.size());
        return out;
    }

    private AiPlugin requirePlugin(String pluginCode) {
        AiPlugin p = pluginMapper.selectOne(new LambdaQueryWrapper<AiPlugin>()
                .eq(AiPlugin::getPluginCode, pluginCode));
        if (p == null) {
            throw new IllegalArgumentException("插件不存在: " + pluginCode);
        }
        return p;
    }

    private void requireAgent(String agentCode) {
        AgentProfile profile = profileMapper.selectOne(new LambdaQueryWrapper<AgentProfile>()
                .eq(AgentProfile::getAgentCode, agentCode));
        if (profile == null) {
            throw new IllegalArgumentException("智能体不存在: " + agentCode);
        }
    }
}
