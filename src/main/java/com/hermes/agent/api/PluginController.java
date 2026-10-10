package com.hermes.agent.api;

import com.hermes.agent.entity.AiPlugin;
import com.hermes.agent.service.PluginService;
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
 * 插件管理 API（补充项「插件」）：注册/启停/绑定智能体 + 装载一致性核验。
 */
@RestController
@RequestMapping("/api/admin/plugins")
@RequiredArgsConstructor
public class PluginController {

    private final PluginService service;

    @GetMapping
    public List<AiPlugin> list() {
        return service.list();
    }

    /** 注册或更新插件（manifest 为 JSON 字符串：{"tools":[],"skills":[],"commands":[],"prompt":""}） */
    @PostMapping
    public AiPlugin save(@RequestBody AiPlugin plugin) {
        return service.save(plugin);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        service.delete(id);
        return Map.of("success", true);
    }

    /** 启用/停用（停用即从运行时卸载） */
    @PostMapping("/{id}/enabled")
    public AiPlugin setEnabled(@PathVariable Long id, @RequestParam boolean enabled) {
        return service.setEnabled(id, enabled);
    }

    /** 绑定到智能体 */
    @PostMapping("/bind")
    public Map<String, Object> bind(@RequestParam String agentCode, @RequestParam String pluginCode) {
        return service.bind(agentCode, pluginCode);
    }

    /** 解除绑定 */
    @PostMapping("/unbind")
    public Map<String, Object> unbind(@RequestParam String agentCode, @RequestParam String pluginCode) {
        return service.unbind(agentCode, pluginCode);
    }

    /** 某智能体当前绑定的插件（含生效状态） */
    @GetMapping("/by-agent")
    public List<Map<String, Object>> byAgent(@RequestParam String agentCode) {
        return service.pluginsForAgent(agentCode);
    }

    /** 装载一致性核验 */
    @GetMapping("/verify")
    public Map<String, Object> verify() {
        return service.verify();
    }
}
