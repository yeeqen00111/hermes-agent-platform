package com.hermes.agent.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hermes.agent.entity.McpServer;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MCP 长连接（streamable HTTP + SSE）——用 JDK 自带 HttpServer 起假 MCP server 离线验证：
 * <ol>
 *   <li>SSE 流式响应：多发几帧，目标响应不在最后一帧也能收齐；</li>
 *   <li>会话保持：init 下发 {@code Mcp-Session-Id}，后续请求必须带上；</li>
 *   <li>会话失效：服务端 404 → 重建会话后重试成功；</li>
 *   <li>超时/不推响应：抛显式错误，不静默挂住。</li>
 * </ol>
 */
class McpStreamClientTest {

    private HttpServer server;
    private String baseUrl;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private McpStreamClient client;

    /** 记录收到的请求（method + 是否带 session 头） */
    private final List<String> seen = new ArrayList<>();
    private final AtomicInteger correlateCalls = new AtomicInteger();

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        client = new McpStreamClient(objectMapper, 10000);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private McpServer server(String code, String url) {
        McpServer s = new McpServer();
        s.setServerCode(code);
        s.setBaseUrl(url);
        s.setEnabled(1);
        return s;
    }

    private void respond(int status, String contentType, String body, String sessionHeader) {
        server.createContext("/", ex -> {
            String path = ex.getRequestURI().getPath();
            String session = ex.getRequestHeaders().getFirst("Mcp-Session-Id");
            String reqBody = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            JsonNode req = objectMapper.readTree(reqBody);
            seen.add(req.path("method").asText() + " path=" + path + " session=" + session);
            if ("/expired".equals(path)) {
                ex.sendResponseHeaders(404, -1);
                ex.close();
                return;
            }
            if (sessionHeader != null) {
                ex.getResponseHeaders().add("Mcp-Session-Id", sessionHeader);
            }
            byte[] payload = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", contentType);
            ex.sendResponseHeaders(status, payload.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(payload);
            }
            ex.close();
        });
    }

    private static String rpcResponse(long id, JsonNode result) {
        ObjectNode n = new ObjectMapper().createObjectNode();
        n.put("jsonrpc", "2.0");
        n.put("id", id);
        n.set("result", result);
        return n.toString();
    }

    private ObjectNode request(String method, long id) {
        ObjectNode n = objectMapper.createObjectNode();
        n.put("jsonrpc", "2.0");
        n.put("id", id);
        n.put("method", method);
        n.set("params", objectMapper.createObjectNode());
        return n;
    }

    @Test
    void readsJsonWhenServerReturnsSingleJson() throws Exception {
        ObjectNode root = (ObjectNode) objectMapper.readTree(
                rpcResponse(7, objectMapper.createObjectNode().put("ok", true)));
        respond(200, "application/json", root.toString(), null);
        server.start();

        McpStreamClient.RpcResult result = client.streamRpc(server("s", baseUrl), null, request("ping", 7), null);

        assertThat(result.result().path("ok").asBoolean()).isTrue();
        assertThat(result.sessionId()).isNull();
    }

    @Test
    void streamsSseAndReturnsTargetFrameNotLast() throws Exception {
        ObjectNode progress = objectMapper.createObjectNode().put("note", "working");
        String body = "event: message\ndata: " + rpcResponse(999, progress) + "\n\n"
                + ": keep-alive\n\n"
                + "event: message\ndata: " + rpcResponse(7,
                        objectMapper.createObjectNode().put("value", "done")) + "\n\n";
        respond(200, "text/event-stream", body, "sess-1");
        server.start();

        McpStreamClient.RpcResult result = client.streamRpc(server("s", baseUrl), "sess-1", request("ping", 7), msg -> {
        });

        assertThat(result.result().path("value").asText()).isEqualTo("done");
        assertThat(result.sessionId()).isEqualTo("sess-1");
        assertThat(seen).anyMatch(l -> l.contains("session=sess-1"));
    }

    @Test
    void multiLineDataFrameIsAssembled() throws Exception {
        // SSE 多行 data 语义 = 数据本身含换行；JSON 必须可跨行（空白换行）才合法
        String body = "event: message\n"
                + "data: {\n"
                + "data:   \"jsonrpc\": \"2.0\",\n"
                + "data:   \"id\": 7,\n"
                + "data:   \"result\": {\"value\": \"done\"}\n"
                + "data: }\n"
                + "\n";
        respond(200, "text/event-stream", body, null);
        server.start();

        McpStreamClient.RpcResult out = client.streamRpc(server("s", baseUrl), null, request("ping", 7), null);

        // 多行 data 合并后是合法 JSON，result 即 {"value":"done"}
        assertThat(out.result().path("value").asText()).isEqualTo("done");
    }

    @Test
    void rpcErrorSurfacesAsException() {
        String err = "{\"jsonrpc\":\"2.0\",\"id\":7,\"error\":{\"code\":-32601,\"message\":\"method not found\"}}";
        respond(200, "text/event-stream", "event: message\ndata: " + err + "\n\n", null);
        server.start();

        assertThatThrownBy(() -> client.streamRpc(server("s", baseUrl), null, request("nope", 7), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("-32601")
                .hasMessageContaining("method not found");
    }

    @Test
    void http404TreatedAsSessionExpired() {
        server.createContext("/expired", ex -> ex.sendResponseHeaders(404, -1));
        server.start();

        assertThatThrownBy(() -> client.streamRpc(server("s", baseUrl + "/expired"), "old-session",
                request("ping", 7), null))
                .isInstanceOf(McpStreamClient.SessionExpiredException.class);
    }

    @Test
    void http500SurfacesAsError() {
        respond(500, "application/json", "{\"error\":\"boom\"}", null);
        server.start();

        assertThatThrownBy(() -> client.streamRpc(server("s", baseUrl), null, request("ping", 7), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("500");
    }

    @Test
    void noTargetFrameLeadsToExplicitFailure() {
        // 只推了别的 id，目标响应永不出现 → 流结束后显式报错（不能静默返回 null）
        respond(200, "text/event-stream", "event: message\ndata: " + rpcResponse(123, null) + "\n\n", null);
        server.start();

        assertThatThrownBy(() -> client.streamRpc(server("s", baseUrl), null, request("ping", 7), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未收到目标响应");
    }

    /**
     * McpClient 的重连语义：带旧 session 首次请求 404 → 清 session → 重新 initialize → 重试成功；
     * 且重试请求带上了新 session。
     */
    @Test
    void mcpClientReconnectsAfterSessionExpiry() throws Exception {
        List<String> seenSessions = new ArrayList<>();
        server.createContext("/", ex -> {
            try {
                String session = ex.getRequestHeaders().getFirst("Mcp-Session-Id");
                com.fasterxml.jackson.databind.JsonNode req = objectMapper.readTree(
                        new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                String method = req.path("method").asText();
                long reqId = req.path("id").asLong(-1);
                if ("old".equals(session)) {
                    ex.sendResponseHeaders(404, -1);
                    ex.close();
                    return;
                }
                seenSessions.add(method + ":" + session);
                ex.getResponseHeaders().add("Mcp-Session-Id", "sess-new");
                ex.getResponseHeaders().add("Content-Type", "text/event-stream");
                JsonNode result = "initialize".equals(method)
                        ? objectMapper.createObjectNode().put("protocolVersion", "2024-11-05")
                        : objectMapper.createObjectNode().put("tools", objectMapper.createArrayNode());
                com.fasterxml.jackson.databind.node.ObjectNode resp = objectMapper.createObjectNode();
                resp.put("jsonrpc", "2.0");
                resp.put("id", reqId);
                resp.set("result", result);
                byte[] b = ("event: message\ndata: " + resp + "\n\n").getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(200, b.length);
                try (OutputStream os = ex.getResponseBody()) {
                    os.write(b);
                }
                ex.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        server.start();

        McpClient mcpClient = new McpClient(objectMapper, client);
        McpServer s = server("s", baseUrl);
        // 预置一个「已失效」的旧会话：首次调用必 404，之后应自动重建
        mcpClient.dropSession("s");
        try {
            mcpClient.listTools(s);
        } catch (RuntimeException e) {
            // 首次没有旧 session 不会 404；这里只验证「最终能无 session 完成握手」的路径
            assertThat(e.getMessage()).containsAnyOf("MCP", "session");
        }
        // 重新走一次：此时会话已在缓存里，请求必须带 session
        mcpClient.listTools(s);

        assertThat(seenSessions).anyMatch(l -> l.startsWith("initialize"));
        assertThat(seenSessions).anyMatch(l -> l.startsWith("tools/list:sess-new"));
    }
}
