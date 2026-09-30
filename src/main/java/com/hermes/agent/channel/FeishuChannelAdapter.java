package com.hermes.agent.channel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hermes.agent.entity.AiChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 飞书渠道出站适配器（HTTP API 对接位）。
 * 完整 WebSocket 长连接 + 流式卡片由渠道适配器单元承担（agent-platform §9.2/§9.4），
 * 此处提供 tenant_access_token 交换与消息发送的最小闭环，凭据只从环境变量解析。
 */
@Slf4j
@Component
public class FeishuChannelAdapter implements ChannelAdapter {

    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final String baseUrl;
    private final Map<String, TokenCache> tokenCache = new ConcurrentHashMap<>();

    public FeishuChannelAdapter(ObjectMapper objectMapper,
                                @Value("${hermes.channel.feishu.base-url:https://open.feishu.cn}") String baseUrl) {
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(15000);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public String type() {
        return "FEISHU";
    }

    @Override
    public ChannelSendResult sendText(AiChannel channel, String receiveId, String text) {
        return send(channel, receiveId, "text", textContent(text));
    }

    @Override
    public ChannelSendResult sendCard(AiChannel channel, String receiveId, String cardJson) {
        return send(channel, receiveId, "interactive", cardJson);
    }

    private ChannelSendResult send(AiChannel channel, String receiveId, String msgType, String content) {
        String appSecret = resolveSecret(channel.getAppSecretRef());
        if (appSecret == null) {
            return ChannelSendResult.fail("CHANNEL_CREDENTIAL_UNRESOLVED",
                    "app_secret_ref 未配置或环境变量未解析: " + channel.getAppSecretRef());
        }
        try {
            String token = getTenantAccessToken(channel, appSecret);
            String body = objectMapper.writeValueAsString(
                    Map.of("receive_id", receiveId, "msg_type", msgType, "content", content));
            String resp = restClient.post()
                    .uri(baseUrl + "/open-apis/im/v1/messages?receive_id_type=open_id")
                    .headers(h -> {
                        h.setContentType(MediaType.APPLICATION_JSON);
                        h.setBearerAuth(token);
                    })
                    .body(body)
                    .retrieve()
                    .body(String.class);
            JsonNode node = objectMapper.readTree(resp);
            if (node.path("code").asInt(0) != 0) {
                return ChannelSendResult.fail("CHANNEL_SEND_FAILED",
                        "飞书发送失败: code=" + node.path("code").asInt() + " msg=" + node.path("msg").asText());
            }
            return ChannelSendResult.success();
        } catch (Exception e) {
            log.warn("飞书渠道发送失败: channel={}, receiveId={}: {}", channel.getChannelCode(), receiveId, e.getMessage());
            return ChannelSendResult.fail("CHANNEL_SEND_FAILED", "飞书发送失败: " + e.getMessage());
        }
    }

    private String getTenantAccessToken(AiChannel channel, String appSecret) {
        TokenCache cached = tokenCache.get(channel.getChannelCode());
        if (cached != null && cached.expireAt() > System.currentTimeMillis()) {
            return cached.token();
        }
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "app_id", channel.getAppId() == null ? "" : channel.getAppId(),
                    "app_secret", appSecret));
            String resp = restClient.post()
                    .uri(baseUrl + "/open-apis/auth/v3/tenant_access_token/internal")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            JsonNode node = objectMapper.readTree(resp);
            if (node.path("code").asInt(0) != 0) {
                throw new IllegalStateException("token交换失败: code=" + node.path("code").asInt()
                        + " msg=" + node.path("msg").asText());
            }
            String token = node.path("tenant_access_token").asText();
            long expireAt = System.currentTimeMillis() + (node.path("expire").asLong(7200L) - 60) * 1000;
            tokenCache.put(channel.getChannelCode(), new TokenCache(token, expireAt));
            return token;
        } catch (Exception e) {
            throw new IllegalStateException("飞书tenant_access_token获取失败: " + e.getMessage());
        }
    }

    private String resolveSecret(String appSecretRef) {
        if (appSecretRef == null || appSecretRef.isBlank()) {
            return null;
        }
        String secret = System.getenv(appSecretRef);
        return secret == null || secret.isBlank() ? null : secret;
    }

    private String textContent(String text) {
        try {
            return objectMapper.writeValueAsString(Map.of("text", text == null ? "" : text));
        } catch (Exception e) {
            return "{\"text\":\"\"}";
        }
    }

    private record TokenCache(String token, long expireAt) {
    }
}
