package com.hermes.agent.channel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hermes.agent.entity.AiChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 企微渠道出站适配器：gettoken（应用）+ 应用消息 message/send。
 * 凭据只从环境变量解析（{@code app_id}=corpid，{@code app_secret_ref}=corpsecret）；
 * {@code config} JSON 需含 {@code agentId}（应用 AgentId）。
 */
@Slf4j
@Component
public class WecomChannelAdapter implements ChannelAdapter {

    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final String baseUrl;
    private final Map<String, TokenCache> tokenCache = new ConcurrentHashMap<>();

    public WecomChannelAdapter(ObjectMapper objectMapper,
                               @Value("${hermes.channel.wecom.base-url:https://qyapi.weixin.qq.com}") String baseUrl) {
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(15000);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public String type() {
        return "WECOM";
    }

    @Override
    public ChannelSendResult sendText(AiChannel channel, String receiveId, String text) {
        String agentId = ChannelConfigs.value(objectMapper, channel.getConfig(), "agentId");
        if (agentId == null) {
            return ChannelSendResult.fail("CHANNEL_CONFIG_MISSING",
                    "企微渠道缺少 config.agentId（应用 AgentId）");
        }
        String secret = resolveSecret(channel);
        if (secret == null) {
            return ChannelSendResult.fail("CHANNEL_CREDENTIAL_UNRESOLVED",
                    "app_secret_ref 未配置或环境变量未解析: " + channel.getAppSecretRef());
        }
        try {
            ObjectNode body = baseBody(agentId, receiveId);
            body.put("msgtype", "text");
            body.putObject("text").put("content", text == null ? "" : text);
            return dispatch(channel, secret, body);
        } catch (Exception e) {
            log.warn("企微渠道发送失败: channel={}: {}", channel.getChannelCode(), e.getMessage());
            return ChannelSendResult.fail("CHANNEL_SEND_FAILED", "企微发送失败: " + e.getMessage());
        }
    }

    @Override
    public ChannelSendResult sendCard(AiChannel channel, String receiveId, String cardJson) {
        String agentId = ChannelConfigs.value(objectMapper, channel.getConfig(), "agentId");
        if (agentId == null) {
            return ChannelSendResult.fail("CHANNEL_CONFIG_MISSING",
                    "企微渠道缺少 config.agentId（应用 AgentId）");
        }
        String secret = resolveSecret(channel);
        if (secret == null) {
            return ChannelSendResult.fail("CHANNEL_CREDENTIAL_UNRESOLVED",
                    "app_secret_ref 未配置或环境变量未解析: " + channel.getAppSecretRef());
        }
        try {
            JsonNode card = objectMapper.readTree(cardJson);
            String markdown = card.path("markdown").asText(null);
            if (markdown == null) {
                markdown = card.path("text").asText(null);
            }
            if (markdown == null) {
                String title = card.path("title").asText("");
                markdown = title.isBlank() ? null : "**" + title + "**";
            }
            if (markdown == null) {
                return ChannelSendResult.fail("CHANNEL_CARD_INVALID",
                        "企微卡片需含 markdown/text/title（markdown 消息）");
            }
            ObjectNode body = baseBody(agentId, receiveId);
            body.put("msgtype", "markdown");
            body.putObject("markdown").put("content", markdown);
            return dispatch(channel, secret, body);
        } catch (Exception e) {
            log.warn("企微卡片发送失败: channel={}: {}", channel.getChannelCode(), e.getMessage());
            return ChannelSendResult.fail("CHANNEL_SEND_FAILED", "企微卡片发送失败: " + e.getMessage());
        }
    }

    private ObjectNode baseBody(String agentId, String receiveId) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("touser", receiveId == null ? "@all" : receiveId);
        body.put("agentid", Integer.parseInt(agentId));
        return body;
    }

    private ChannelSendResult dispatch(AiChannel channel, String secret, ObjectNode body) throws Exception {
        String token = accessToken(channel, secret);
        String resp = restClient.post()
                .uri(baseUrl + "/cgi-bin/message/send?access_token=" + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body.toString())
                .retrieve()
                .body(String.class);
        JsonNode node = objectMapper.readTree(resp);
        int errcode = node.path("errcode").asInt(-1);
        if (errcode != 0) {
            return ChannelSendResult.fail("CHANNEL_SEND_FAILED",
                    "企微发送失败: errcode=" + errcode + " errmsg=" + node.path("errmsg").asText());
        }
        return ChannelSendResult.success();
    }

    private String accessToken(AiChannel channel, String secret) {
        TokenCache cached = tokenCache.get(channel.getChannelCode());
        if (cached != null && cached.expireAt() > System.currentTimeMillis()) {
            return cached.token();
        }
        try {
            String uri = baseUrl + "/cgi-bin/gettoken?corpid=" + enc(channel.getAppId())
                    + "&corpsecret=" + enc(secret);
            String resp = restClient.get().uri(uri).retrieve().body(String.class);
            JsonNode node = objectMapper.readTree(resp);
            int errcode = node.path("errcode").asInt(-1);
            if (errcode != 0) {
                throw new IllegalStateException("gettoken 失败: errcode=" + errcode
                        + " errmsg=" + node.path("errmsg").asText());
            }
            String token = node.path("access_token").asText();
            long expireAt = System.currentTimeMillis()
                    + (node.path("expires_in").asLong(7200L) - 60) * 1000;
            tokenCache.put(channel.getChannelCode(), new TokenCache(token, expireAt));
            return token;
        } catch (Exception e) {
            throw new IllegalStateException("企微 access_token 获取失败: " + e.getMessage());
        }
    }

    private String resolveSecret(AiChannel channel) {
        String ref = channel.getAppSecretRef();
        if (ref == null || ref.isBlank()) {
            return null;
        }
        String secret = System.getenv(ref);
        return secret == null || secret.isBlank() ? null : secret;
    }

    private static String enc(String v) {
        return URLEncoder.encode(v == null ? "" : v, StandardCharsets.UTF_8);
    }

    private record TokenCache(String token, long expireAt) {
    }
}
