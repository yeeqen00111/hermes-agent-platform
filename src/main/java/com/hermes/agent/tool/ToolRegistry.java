package com.hermes.agent.tool;

import com.hermes.agent.common.enums.SafetyLevel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具注册表
 */
@Slf4j
@Component
public class ToolRegistry {

    private final Map<String, ToolDefinition> tools = new ConcurrentHashMap<>();

    /**
     * 注册工具
     */
    public void register(ToolDefinition definition) {
        if (definition.getSafetyLevel() == SafetyLevel.FORBIDDEN) {
            log.warn("拒绝注册FORBIDDEN级工具: {}", definition.getToolCode());
            return;
        }
        tools.put(definition.getToolCode(), definition);
        log.info("注册工具: {} (安全等级: {})", definition.getToolCode(), definition.getSafetyLevel());
    }

    /**
     * 获取工具定义
     */
    public Optional<ToolDefinition> getTool(String toolCode) {
        return Optional.ofNullable(tools.get(toolCode));
    }

    /**
     * 获取所有启用的工具
     */
    public Collection<ToolDefinition> getAllEnabledTools() {
        return tools.values().stream()
                .filter(ToolDefinition::getEnabled)
                .toList();
    }

    /**
     * 检查工具是否存在且启用
     */
    public boolean isToolAvailable(String toolCode) {
        ToolDefinition def = tools.get(toolCode);
        return def != null && def.getEnabled();
    }

    /**
     * 注销工具
     */
    public void unregister(String toolCode) {
        tools.remove(toolCode);
        log.info("注销工具: {}", toolCode);
    }

    /**
     * 批量注册
     */
    public void registerAll(Collection<ToolDefinition> definitions) {
        definitions.forEach(this::register);
    }
}
