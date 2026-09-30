package com.hermes.agent.mcp;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hermes.agent.common.enums.SafetyLevel;
import com.hermes.agent.dto.ToolRequest;
import com.hermes.agent.dto.ToolResponse;
import com.hermes.agent.entity.McpServer;
import com.hermes.agent.entity.McpTool;
import com.hermes.agent.mapper.McpServerMapper;
import com.hermes.agent.mapper.McpToolMapper;
import com.hermes.agent.tool.ToolDefinition;
import com.hermes.agent.tool.ToolRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MCP 扩展位服务：服务器 CRUD + 工具归一化同步（§5.4）+ 调用分发。
 * 归一化规则：toolCode=mcp.<server>.<tool>；safetyLevel 默认 READ；不注入 scopeFields；
 * citations=[{kind:"mcp", source:<server>, title:<tool>}]。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpService {

    private static final int MCP_TIMEOUT_MS = 30000;

    private final McpServerMapper serverMapper;
    private final McpToolMapper toolMapper;
    private final ToolRegistry toolRegistry;
    private final McpClient mcpClient;
    private final ObjectMapper objectMapper;

    // ---------- 管理面 ----------

    public List<McpServer> listServers() {
        return serverMapper.selectList(new LambdaQueryWrapper<McpServer>()
                .orderByDesc(McpServer::getId));
    }

    public McpServer getServer(String serverCode) {
        return serverMapper.selectOne(new LambdaQueryWrapper<McpServer>()
                .eq(McpServer::getServerCode, serverCode));
    }

    public McpServer saveServer(McpServer server) {
        if (server.getServerCode() == null || server.getServerCode().isBlank()
                || server.getBaseUrl() == null || server.getBaseUrl().isBlank()) {
            throw new IllegalArgumentException("serverCode 与 baseUrl 必填");
        }
        if (server.getId() != null) {
            serverMapper.updateById(server);
        } else {
            serverMapper.insert(server);
        }
        return server;
    }

    public void deleteServer(String serverCode) {
        McpServer server = getServer(serverCode);
        if (server != null) {
            serverMapper.deleteById(server.getId());
        }
        List<McpTool> tools = listTools(serverCode);
        for (McpTool tool : tools) {
            toolMapper.deleteById(tool.getId());
            toolRegistry.unregister(tool.getToolCode());
        }
        log.info("MCP服务器已删除: {}, 连带注销工具 {} 个", serverCode, tools.size());
    }

    public List<McpTool> listTools(String serverCode) {
        return toolMapper.selectList(new LambdaQueryWrapper<McpTool>()
                .eq(McpTool::getServerCode, serverCode)
                .orderByAsc(McpTool::getToolCode));
    }

    /**
     * 同步：拉取远端工具清单，归一化后落库并注册进 ToolRegistry；远端已不存在的工具下线。
     */
    public Map<String, Object> sync(String serverCode) {
        McpServer server = getServer(serverCode);
        if (server == null) {
            throw new IllegalArgumentException("MCP服务器不存在: " + serverCode);
        }
        List<McpClient.RemoteTool> remoteTools = mcpClient.listTools(server);
        Set<String> remoteCodes = new HashSet<>();
        for (McpClient.RemoteTool rt : remoteTools) {
            String toolCode = "mcp." + serverCode + "." + rt.name();
            remoteCodes.add(toolCode);
            upsertToolRow(serverCode, toolCode, rt);
            toolRegistry.register(toDefinition(server, toolCode, rt));
        }
        int removed = 0;
        for (McpTool local : listTools(serverCode)) {
            if (!remoteCodes.contains(local.getToolCode())) {
                toolMapper.deleteById(local.getId());
                toolRegistry.unregister(local.getToolCode());
                removed++;
            }
        }
        log.info("MCP同步完成: server={}, synced={}, removed={}",
                serverCode, remoteTools.size(), removed);
        return Map.of("serverCode", serverCode, "synced", remoteTools.size(), "removed", removed);
    }

    private void upsertToolRow(String serverCode, String toolCode, McpClient.RemoteTool rt) {
        toolMapper.delete(new LambdaQueryWrapper<McpTool>().eq(McpTool::getToolCode, toolCode));
        McpTool row = new McpTool();
        row.setToolCode(toolCode);
        row.setServerCode(serverCode);
        row.setDisplayName(rt.name());
        row.setSafetyLevel(SafetyLevel.READ.name());
        row.setEnabled(1);
        try {
            row.setParamSchema(objectMapper.writeValueAsString(rt.inputSchema()));
        } catch (Exception e) {
            row.setParamSchema("{}");
        }
        toolMapper.insert(row);
    }

    private ToolDefinition toDefinition(McpServer server, String toolCode, McpClient.RemoteTool rt) {
        ToolDefinition def = new ToolDefinition();
        def.setToolCode(toolCode);
        def.setDisplayName(rt.name());
        def.setSafetyLevel(SafetyLevel.READ);
        def.setParamSchema(rt.inputSchema());
        def.setTimeoutMs(MCP_TIMEOUT_MS);
        def.setDescription(rt.description());
        def.setEnabled(server.getEnabled() == null || server.getEnabled() == 1);
        return def;
    }

    // ---------- 运行时调用 ----------

    public ToolResponse callTool(String toolCode, ToolRequest request, long startTime) {
        McpTool row = toolMapper.selectOne(new LambdaQueryWrapper<McpTool>()
                .eq(McpTool::getToolCode, toolCode)
                .eq(McpTool::getEnabled, 1));
        if (row == null) {
            return error(toolCode, "MCP_TOOL_NOT_REGISTERED",
                    "MCP工具未同步注册: " + toolCode + "（请先对服务器执行 sync）", startTime);
        }
        McpServer server = getServer(row.getServerCode());
        if (server == null || server.getEnabled() == null || server.getEnabled() == 0) {
            return error(toolCode, "MCP_SERVER_DISABLED",
                    "MCP服务器未启用: " + row.getServerCode(), startTime);
        }
        String toolName = toolCode.substring(("mcp." + row.getServerCode() + ".").length());
        try {
            String content = mcpClient.callTool(server, toolName, request.getArguments());
            ToolResponse response = new ToolResponse();
            response.setSuccess(true);
            response.setData(Map.of("content", content));
            ToolResponse.Citation citation = new ToolResponse.Citation();
            citation.setKind("mcp");
            citation.setSource(row.getServerCode());
            citation.setTitle(toolName);
            response.setCitations(List.of(citation));
            response.setDurationMs(System.currentTimeMillis() - startTime);
            return response;
        } catch (Exception e) {
            log.warn("MCP工具调用失败: server={}, tool={}: {}",
                    row.getServerCode(), toolName, e.getMessage());
            return error(toolCode, "MCP_CALL_ERROR",
                    "MCP工具调用失败: " + e.getMessage(), startTime);
        }
    }

    private ToolResponse error(String toolCode, String errorCode, String message, long startTime) {
        ToolResponse response = new ToolResponse();
        response.setSuccess(false);
        response.setErrorCode(errorCode);
        response.setErrorMessage(message);
        response.setDurationMs(System.currentTimeMillis() - startTime);
        return response;
    }

    /**
     * 启动完成后把已同步的 MCP 工具回填进内存注册表（本地数据，无远端调用）
     */
    @EventListener(ApplicationReadyEvent.class)
    public void rehydrateRegistry() {
        List<McpServer> servers = serverMapper.selectList(new LambdaQueryWrapper<McpServer>()
                .eq(McpServer::getEnabled, 1));
        Map<String, McpServer> byCode = new HashMap<>();
        servers.forEach(s -> byCode.put(s.getServerCode(), s));
        int count = 0;
        for (McpTool row : toolMapper.selectList(new LambdaQueryWrapper<McpTool>()
                .eq(McpTool::getEnabled, 1))) {
            if (!byCode.containsKey(row.getServerCode())) {
                continue;
            }
            ToolDefinition def = new ToolDefinition();
            def.setToolCode(row.getToolCode());
            def.setDisplayName(row.getDisplayName() == null ? row.getToolCode() : row.getDisplayName());
            def.setSafetyLevel(safeLevelOf(row.getSafetyLevel()));
            def.setParamSchema(parseSchema(row.getParamSchema()));
            def.setTimeoutMs(MCP_TIMEOUT_MS);
            def.setEnabled(true);
            toolRegistry.register(def);
            count++;
        }
        log.info("MCP工具注册表回填完成: {} 个工具", count);
    }

    private SafetyLevel safeLevelOf(String name) {
        try {
            return name == null ? SafetyLevel.READ : SafetyLevel.valueOf(name);
        } catch (IllegalArgumentException e) {
            return SafetyLevel.READ;
        }
    }

    private Map<String, Object> parseSchema(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<>() {
            });
        } catch (Exception e) {
            return Map.of();
        }
    }
}
