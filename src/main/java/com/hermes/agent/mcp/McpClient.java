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

/**
 * MCP 客户端：JSON-RPC 2.0 over HTTP（streamable HTTP 按需扩展）。
 * 只做点对点调用，凭据仅从环境变量解析，不进日志。
 */
@Slf4j
@Component
public class McpClient {

    private static final String PROTOCOL_VERSION = "2024-11-05";

    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public McpClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
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
        ObjectNode body = objectMapper.createObjectNode();
        body.put("jsonrpc", "2.0");
        body.put("id", System.nanoTime());
        body.put("method", method);
        body.set("params", params);
        String respText = restClient.post()
                .uri(server.getBaseUrl())
                .headers(h -> {
                    h.setContentType(MediaType.APPLICATION_JSON);
                    String apiKey = resolveApiKey(server.getApiKeyRef());
                    if (apiKey != null) {
                        h.setBearerAuth(apiKey);
                    }
                })
                .body(body.toString())
                .retrieve()
                .onStatus(st -> st.value() >= 400, (req, resp) -> {
                    throw new IllegalStateException("MCP 服务 HTTP " + resp.getStatusCode().value()
                            + ": " + server.getServerCode());
                })
                .body(String.class);
        JsonNode node;
        try {
            node = objectMapper.readTree(respText);
        } catch (Exception e) {
            throw new IllegalStateException("MCP 服务响应非JSON: " + server.getServerCode());
        }
        if (node.has("error")) {
            JsonNode err = node.path("error");
            throw new IllegalStateException("MCP RPC错误 " + err.path("code").asText()
                    + ": " + err.path("message").asText(""));
        }
        return node.path("result");
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
