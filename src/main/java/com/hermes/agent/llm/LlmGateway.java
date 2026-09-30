package com.hermes.agent.llm;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hermes.agent.entity.AgentProfile;
import com.hermes.agent.entity.ModelProvider;
import com.hermes.agent.mapper.ModelProviderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM Gateway：OpenAI 兼容协议统一出口。
 * 供应商配置在 ai_model_provider，密钥只存引用（环境变量名），无密钥时回退 mock，保证开发期零依赖可跑。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmGateway {

    /** mock 模式下触发工具调用的标记，仅用于联调 */
    private static final String MOCK_TOOL_MARKER = "#mocktool";
    private static final Pattern MOCK_TOOL = Pattern.compile("#mocktool\\s+([\\w.\\-]+)(?:\\s+(\\{.*}))?");

    private final ModelProviderMapper providerMapper;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public String chat(AgentProfile profile, List<LlmMessage> messages) {
        return stream(profile, messages, null);
    }

    /**
     * 流式调用；onDelta 为 null 时仅返回完整文本
     */
    public String stream(AgentProfile profile, List<LlmMessage> messages, Consumer<String> onDelta) {
        ModelProvider provider = resolveProvider(profile.getModelProvider());
        String apiKey = provider == null ? null : resolveSecret(provider.getApiKeyRef());
        if (provider == null || apiKey == null) {
            return mockStream(profile, messages, onDelta);
        }
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", profile.getModelName());
            body.put("temperature", profile.getTemperature() == null ? 0.7 : profile.getTemperature());
            body.put("max_tokens", profile.getMaxTokens() == null ? 2000 : profile.getMaxTokens());
            body.put("stream", onDelta != null);
            ArrayNode arr = body.putArray("messages");
            for (LlmMessage m : messages) {
                ObjectNode mo = arr.addObject();
                mo.put("role", m.getRole());
                mo.put("content", m.getContent());
            }
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(trimSlash(provider.getBaseUrl()) + "/chat/completions"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofMillis(profile.getTimeoutMs() == null ? 60000 : profile.getTimeoutMs()))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            if (onDelta == null) {
                HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                JsonNode root = objectMapper.readTree(resp.body());
                return root.path("choices").path(0).path("message").path("content").asText("");
            }
            HttpResponse<java.io.InputStream> resp =
                    httpClient.send(req, HttpResponse.BodyHandlers.ofInputStream());
            StringBuilder full = new StringBuilder();
            try (var reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(resp.body(), java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data:")) {
                        continue;
                    }
                    String payload = line.substring(5).trim();
                    if ("[DONE]".equals(payload)) {
                        break;
                    }
                    String delta = objectMapper.readTree(payload)
                            .path("choices").path(0).path("delta").path("content").asText("");
                    if (!delta.isEmpty()) {
                        full.append(delta);
                        onDelta.accept(delta);
                    }
                }
            }
            return full.toString();
        } catch (Exception e) {
            log.warn("LLM调用失败，回退mock: {}", e.getMessage());
            return mockStream(profile, messages, onDelta);
        }
    }

    private ModelProvider resolveProvider(String providerCode) {
        if (providerCode == null || providerCode.isBlank()) {
            return null;
        }
        return providerMapper.selectOne(new LambdaQueryWrapper<ModelProvider>()
                .eq(ModelProvider::getProviderCode, providerCode)
                .eq(ModelProvider::getEnabled, 1));
    }

    private String resolveSecret(String ref) {
        if (ref == null || ref.isBlank()) {
            return null;
        }
        String v = System.getenv(ref);
        return (v == null || v.isBlank()) ? null : v;
    }

    private String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /**
     * 无密钥时的兜底回复。
     * 额外支持 `#mocktool <toolCode> [json参数]` 触发一次工具调用，用于审批门等链路的确定性联调。
     */
    private String mockStream(AgentProfile profile, List<LlmMessage> messages, Consumer<String> onDelta) {
        String lastUser = messages.stream()
                .filter(m -> "user".equals(m.getRole()))
                .reduce((a, b) -> b)
                .map(LlmMessage::getContent)
                .orElse("");

        boolean toolAlreadyCalled = messages.stream().anyMatch(m -> "tool".equals(m.getRole()));
        if (!toolAlreadyCalled && lastUser.contains(MOCK_TOOL_MARKER)) {
            String toolCall = buildMockToolCall(lastUser);
            if (toolCall != null) {
                return toolCall;
            }
        }

        String reply = "[mock:" + profile.getModelName() + "] 已收到请求：" + lastUser;
        if (onDelta == null) {
            return reply;
        }
        for (int i = 0; i < reply.length(); i += 8) {
            onDelta.accept(reply.substring(i, Math.min(i + 8, reply.length())));
        }
        return reply;
    }

    private String buildMockToolCall(String lastUser) {
        Matcher m = MOCK_TOOL.matcher(lastUser);
        if (!m.find()) {
            return null;
        }
        String toolCode = m.group(1);
        String arguments = m.group(2) == null || m.group(2).isBlank()
                ? "{\"environment\":\"dev\"}"
                : m.group(2).trim();
        return "```tool_call\n{\"tool\":\"" + toolCode + "\",\"arguments\":" + arguments + "}\n```";
    }
}
