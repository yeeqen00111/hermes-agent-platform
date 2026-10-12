package com.hermes.agent.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hermes.agent.entity.McpServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * MCP streamable HTTP 客户端：一次 JSON-RPC 请求走「POST + 长连接流式响应」，
 * 支持 {@code Mcp-Session-Id} 会话保持与 SSE 多帧推送（服务端边算边推，不必等连接关闭）。
 *
 * <p>与 {@link McpClient} 的关系：{@code McpClient} 负责请求编排与 session 缓存，
 * 本类只负责「发一次请求、按帧收完、按 id 路由响应」。
 */
@Slf4j
@Component
public class McpStreamClient {

    /** 会话失效（服务端 404/400 常见的 "session not found"），由调用方重连后重试 */
    public static class SessionExpiredException extends IllegalStateException {
        public SessionExpiredException(String message) {
            super(message);
        }
    }

    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public McpStreamClient(ObjectMapper objectMapper,
                           @Value("${hermes.mcp.stream.read-timeout-ms:60000}") long readTimeoutMs) {
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout((int) readTimeoutMs);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    /** 一次流式 RPC 的结果：目标 id 的 result + 服务端下发的（可能新的）session id */
    public record RpcResult(JsonNode result, String sessionId) {
    }

    /**
     * 发起一次请求并流式读取响应。
     *
     * @param sessionId 已建立的会话（可为 null，表示尚未 initialize）
     * @param request   完整 JSON-RPC 请求体（含 id）
     * @param onEvent   每收到一帧回调一次（progress / log 等中间帧），可为 null
     * @return 匹配 {@code request.id} 的 result，以及响应头里的 session id
     */
    public RpcResult streamRpc(McpServer server, String sessionId, ObjectNode request,
                               Consumer<JsonNode> onEvent) {
        long targetId = request.path("id").asLong(-1);
        RpcResult[] holder = new RpcResult[1];
        try {
            restClient.post()
                    .uri(server.getBaseUrl())
                    .header("Accept", "text/event-stream, application/json")
                    .headers(h -> {
                        if (sessionId != null && !sessionId.isBlank()) {
                            h.set("Mcp-Session-Id", sessionId);
                        }
                        String apiKey = resolveApiKey(server.getApiKeyRef());
                        if (apiKey != null) {
                            h.setBearerAuth(apiKey);
                        }
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request.toString())
                    .exchange((req, resp) -> {
                        HttpStatusCode status = resp.getStatusCode();
                        if (status.isSameCodeAs(HttpStatusCode.valueOf(404))
                                || status.isSameCodeAs(HttpStatusCode.valueOf(400))) {
                            // 会话已过期/不存在：清理后由上层重新 initialize
                            throw new SessionExpiredException(
                                    "MCP 会话失效 HTTP " + status.value() + ": " + server.getServerCode());
                        }
                        if (!status.is2xxSuccessful()) {
                            throw new IllegalStateException("MCP 服务 HTTP " + status.value()
                                    + ": " + server.getServerCode());
                        }
                        String newSession = resp.getHeaders().getFirst("Mcp-Session-Id");
                        MediaType contentType = resp.getHeaders().getContentType();
                        boolean stream = contentType != null
                                && contentType.isCompatibleWith(MediaType.TEXT_EVENT_STREAM);
                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(resp.getBody(), StandardCharsets.UTF_8))) {
                            if (stream) {
                                readSse(reader, targetId, newSession, onEvent, holder);
                            } else {
                                // 兼容只回单包 JSON 的 MCP server
                                StringBuilder sb = new StringBuilder();
                                try {
                                    String line;
                                    while ((line = reader.readLine()) != null) {
                                        sb.append(line);
                                    }
                                } catch (Exception e) {
                                    throw new IllegalStateException("读取 MCP 响应失败: " + e.getMessage(), e);
                                }
                                try {
                                    matchResponse(sb.toString(), targetId, onEvent, holder);
                                } catch (Exception e) {
                                    throw new IllegalStateException("MCP 响应解析失败: " + e.getMessage(), e);
                                }
                            }
                        }
                        return null;
                    });
        } catch (SessionExpiredException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("MCP 流式调用失败: " + server.getServerCode() + ": " + e.getMessage(), e);
        }
        if (holder[0] == null) {
            throw new IllegalStateException("MCP 流式调用未收到目标响应 id=" + targetId);
        }
        return holder[0];
    }

    /** SSE 逐行流式读取：每凑齐一帧立即处理，不等待连接关闭 */
    private void readSse(BufferedReader reader, long targetId, String newSession,
                         Consumer<JsonNode> onEvent, RpcResult[] holder) {
        SseFramer framer = new SseFramer();
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                SseFramer.Frame frame = framer.feed(line);
                if (frame == null) {
                    continue;
                }
                if (!"message".equals(frame.event())) {
                    continue;
                }
                String data = frame.data();
                if (data.isBlank()) {
                    continue;
                }
                JsonNode msg = objectMapper.readTree(data);
                if (msg.has("error")) {
                    JsonNode err = msg.path("error");
                    throw new IllegalStateException("MCP RPC错误 " + err.path("code").asText()
                            + ": " + err.path("message").asText(""));
                }
                if (msg.path("id").asLong(-1) == targetId) {
                    // 目标响应到达即返回（连接由上层关闭），不必读完整条流
                    holder[0] = new RpcResult(msg.path("result"), newSession);
                    return;
                }
                if (onEvent != null) {
                    onEvent.accept(msg);
                }
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("读取 MCP SSE 流失败: " + e.getMessage(), e);
        }
        log.warn("MCP SSE 流结束但未收到目标响应 id={}", targetId);
    }

    /** 单包 JSON-RPC 响应：可能是对象，也可能是批量数组（逐项匹配） */
    private void matchResponse(String body, long targetId, Consumer<JsonNode> onEvent,
                               RpcResult[] holder) {
        if (body == null || body.isBlank()) {
            throw new IllegalStateException("MCP 服务响应为空");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (Exception e) {
            throw new IllegalStateException("MCP 服务响应非JSON: " + e.getMessage());
        }
        if (root.isArray()) {
            for (JsonNode msg : root) {
                if (msg.path("id").asLong(-1) == targetId) {
                    checkError(msg);
                    holder[0] = new RpcResult(msg.path("result"), null);
                    return;
                }
                if (onEvent != null) {
                    onEvent.accept(msg);
                }
            }
            throw new IllegalStateException("MCP 响应数组中无目标响应 id=" + targetId);
        }
        checkError(root);
        if (root.path("id").asLong(-1) != targetId) {
            throw new IllegalStateException("MCP 响应 id 不匹配: 期望 " + targetId);
        }
        holder[0] = new RpcResult(root.path("result"), null);
    }

    private void checkError(JsonNode msg) {
        if (msg.has("error")) {
            JsonNode err = msg.path("error");
            throw new IllegalStateException("MCP RPC错误 " + err.path("code").asText()
                    + ": " + err.path("message").asText(""));
        }
    }

    private String resolveApiKey(String apiKeyRef) {
        if (apiKeyRef == null || apiKeyRef.isBlank()) {
            return null;
        }
        return System.getenv(apiKeyRef);
    }
}
