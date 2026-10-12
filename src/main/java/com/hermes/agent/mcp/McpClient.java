package com.hermes.agent.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hermes.agent.entity.McpServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP 客户端：streamable HTTP（POST + SSE 长连接），支持 {@code Mcp-Session-Id} 会话保持
 * 与失效重连；只做点对点调用，凭据仅从环境变量解析，不进日志。
 */
@Slf4j
@Component
public class McpClient {

    private static final String PROTOCOL_VERSION = "2024-11-05";

    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final McpStreamClient streamClient;

    /** 已建立的 MCP 会话：serverCode → Mcp-Session-Id */
    private final Map<String, String> sessions = new ConcurrentHashMap<>();

    public McpClient(ObjectMapper objectMapper, McpStreamClient streamClient) {
        this.objectMapper = objectMapper;
        this.streamClient = streamClient;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(30000);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public record RemoteTool(String name, String description, Map<String, Object> inputSchema) {
    }

    /**
     * initialize + tools/list，返回远端工具清单
     */
    public List<RemoteTool> listTools(McpServer server) {
        rpcCall(server, "initialize", initializeParams());
        JsonNode result = rpcCall(server, "tools/list", objectMapper.createObjectNode());
        List<RemoteTool> tools = new ArrayList<>();
        for (JsonNode t : result.path("tools")) {
            Map<String, Object> schema = objectMapper.convertValue(
                    t.path("inputSchema"), new TypeReference<>() {
                    });
            tools.add(new RemoteTool(
                    t.path("name").asText(),
                    t.path("description").asText(""),
                    schema == null ? Map.of() : schema));
        }
        return tools;
    }

    /**
     * tools/call，返回 content 中 text 项拼接的正文
     */
    public String callTool(McpServer server, String toolName, Map<String, Object> arguments) {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", toolName);
        params.set("arguments", objectMapper.valueToTree(arguments == null ? Map.of() : arguments));
        JsonNode result = rpcCall(server, "tools/call", params);
        StringBuilder sb = new StringBuilder();
        for (JsonNode item : result.path("content")) {
            if ("text".equals(item.path("type").asText("text"))) {
                sb.append(item.path("text").asText());
            }
        }
        String text = sb.toString();
        if (result.path("isError").asBoolean(false)) {
            throw new IllegalStateException(text.isBlank() ? "MCP 工具返回错误" : text);
        }
        return text;
    }

    private JsonNode rpcCall(McpServer server, String method, ObjectNode params) {
        ObjectNode request = buildRequest(method, params);
        String session = sessionOf(server);
        try {
            McpStreamClient.RpcResult result = streamClient.streamRpc(server, session, request, msg -> {
                // 中间帧（progress/log 等）忽略；目标响应在结果里
            });
            rememberSession(server, result);
            return result.result();
        } catch (McpStreamClient.SessionExpiredException e) {
            // 会话过期（服务端重启/超时清理）：清理后重新 initialize 再重试一次
            sessions.remove(server.getServerCode());
            log.info("MCP 会话失效，重建后重试: {} ({})", server.getServerCode(), e.getMessage());
            String fresh = initializeSession(server);
            McpStreamClient.RpcResult retry = streamClient.streamRpc(server, fresh, request, msg -> {
            });
            rememberSession(server, retry);
            return retry.result();
        }
    }

    /** 取会话；没有则先 initialize 建一个（失败按无会话继续，兼容单包 server） */
    private String sessionOf(McpServer server) {
        String cached = sessions.get(server.getServerCode());
        if (cached != null) {
            return cached;
        }
        return initializeSession(server);
    }

    private String initializeSession(McpServer server) {
        try {
            McpStreamClient.RpcResult result = streamClient.streamRpc(
                    server, null, buildRequest("initialize", initializeParams()), msg -> {
                    });
            String session = result.sessionId();
            if (session != null) {
                sessions.put(server.getServerCode(), session);
            }
            return session;
        } catch (Exception e) {
            log.warn("MCP initialize 未建立会话，按单包模式继续: {} ({})",
                    server.getServerCode(), e.getMessage());
            return null;
        }
    }

    private void rememberSession(McpServer server, McpStreamClient.RpcResult result) {
        if (result.sessionId() != null && !result.sessionId().isBlank()) {
            sessions.put(server.getServerCode(), result.sessionId());
        }
    }

    private ObjectNode buildRequest(String method, ObjectNode params) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("jsonrpc", "2.0");
        body.put("id", System.nanoTime());
        body.put("method", method);
        body.set("params", params == null ? objectMapper.createObjectNode() : params);
        return body;
    }

    /** 主动断开会话（服务器停用时调用，避免连到已下线服务） */
    public void dropSession(String serverCode) {
        sessions.remove(serverCode);
    }

    private ObjectNode initializeParams() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("protocolVersion", PROTOCOL_VERSION);
        params.set("capabilities", objectMapper.createObjectNode());
        ObjectNode clientInfo = objectMapper.createObjectNode();
        clientInfo.put("name", "hermes-agent-platform");
        clientInfo.put("version", "0.1.0");
        params.set("clientInfo", clientInfo);
        return params;
    }

    private String resolveApiKey(String apiKeyRef) {
        if (apiKeyRef == null || apiKeyRef.isBlank()) {
            return null;
        }
        return System.getenv(apiKeyRef);
    }
}
