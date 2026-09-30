package com.hermes.agent.api;

import com.hermes.agent.entity.McpServer;
import com.hermes.agent.entity.McpTool;
import com.hermes.agent.mcp.McpService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * MCP 扩展位管理 API（服务器 CRUD + 工具同步）
 */
@RestController
@RequestMapping("/api/mcp")
@RequiredArgsConstructor
public class McpController {

    private final McpService mcpService;

    @GetMapping("/servers")
    public List<McpServer> listServers() {
        return mcpService.listServers();
    }

    @PostMapping("/servers")
    public Map<String, Object> saveServer(@RequestBody McpServer server) {
        mcpService.saveServer(server);
        return Map.of("success", true, "message", "MCP服务器已保存");
    }

    @DeleteMapping("/servers/{serverCode}")
    public Map<String, Object> deleteServer(@PathVariable String serverCode) {
        mcpService.deleteServer(serverCode);
        return Map.of("success", true, "message", "MCP服务器已删除");
    }

    @PostMapping("/servers/{serverCode}/sync")
    public Map<String, Object> sync(@PathVariable String serverCode) {
        return mcpService.sync(serverCode);
    }

    @GetMapping("/servers/{serverCode}/tools")
    public List<McpTool> listTools(@PathVariable String serverCode) {
        return mcpService.listTools(serverCode);
    }
}
