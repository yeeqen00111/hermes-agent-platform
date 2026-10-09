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
 * 钉钉渠道出站适配器：gettoken（企业内部应用）+ 工作通知 asyncsend_v2。
 * 凭据只从环境变量解析（{@code app_id}=AppKey，{@code app_secret_ref}=AppSecret）；
 * {@code config} JSON 需含 {@code agentId}（机器人应用 AgentId）。
 */
@Slf4j
@Component
public class DingTalkChannelAdapter implements ChannelAdapter {

    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final String baseUrl;
    private final Map<String, TokenCache> tokenCache = new ConcurrentHashMap<>();

    public DingTalkChannelAdapter(ObjectMapper objectMapper,
                                  @Value("${hermes.channel.dingtalk.base-url:https://oapi.dingtalk.com}") String baseUrl) {
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(15000);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public String type() {
        return "DINGTALK";
    }

    @Override
    public ChannelSendResult sendText(AiChannel channel, String receiveId, String text) {
        String agentId = ChannelConfigs.value(objectMapper, channel.getConfig(), "agentId");
        if (agentId == null) {
            return ChannelSendResult.fail("CHANNEL_CONFIG_MISSING",
                    "钉钉渠道缺少 config.agentId（企业内部应用 AgentId）");
        }
        String secret = resolveSecret(channel);
        if (secret == null) {
            return ChannelSendResult.fail("CHANNEL_CREDENTIAL_UNRESOLVED",
                    "app_secret_ref 未配置或环境变量未解析: " + channel.getAppSecretRef());
        }
        try {
            ObjectNode msg = objectMapper.createObjectNode();
            msg.put("msgtype", "text");
            msg.putObject("text").put("content", text == null ? "" : text);
            return dispatch(channel, agentId, secret, receiveId, msg);
        } catch (Exception e) {
            log.warn("钉钉渠道发送失败: channel={}: {}", channel.getChannelCode(), e.getMessage());
            return ChannelSendResult.fail("CHANNEL_SEND_FAILED", "钉钉发送失败: " + e.getMessage());
        }
    }

    @Override
    public ChannelSendResult sendCard(AiChannel channel, String receiveId, String cardJson) {
        String agentId = ChannelConfigs.value(objectMapper, channel.getConfig(), "agentId");
        if (agentId == null) {
            return ChannelSendResult.fail("CHANNEL_CONFIG_MISSING",
                    "钉钉渠道缺少 config.agentId（企业内部应用 AgentId）");
        }
        String secret = resolveSecret(channel);
        if (secret == null) {
            return ChannelSendResult.fail("CHANNEL_CREDENTIAL_UNRESOLVED",
                    "app_secret_ref 未配置或环境变量未解析: " + channel.getAppSecretRef());
        }
        try {
            JsonNode card = objectMapper.readTree(cardJson);
            String title = card.path("title").asText(null);
            String text = card.path("text").asText(null);
            String singleTitle = card.path("singleTitle").asText(null);
            String singleUrl = card.path("singleUrl").asText(null);
            if (title == null || text == null || singleUrl == null) {
                return ChannelSendResult.fail("CHANNEL_CARD_INVALID",
                        "钉钉卡片需含 title/text/singleUrl（actionCard）");
            }
            ObjectNode actionCard = objectMapper.createObjectNode();
            actionCard.put("title", title);
            actionCard.put("text", text);
            actionCard.put("btnOrientation", "0");
            actionCard.put("singleTitle", singleTitle == null ? "查看" : singleTitle);
            actionCard.put("singleURL", singleUrl);
            ObjectNode msg = objectMapper.createObjectNode();
            msg.put("msgtype", "actionCard");
            msg.set("actionCard", actionCard);
            return dispatch(channel, agentId, secret, receiveId, msg);
        } catch (Exception e) {
            log.warn("钉钉卡片发送失败: channel={}: {}", channel.getChannelCode(), e.getMessage());
            return ChannelSendResult.fail("CHANNEL_SEND_FAILED", "钉钉卡片发送失败: " + e.getMessage());
        }
    }

    private ChannelSendResult dispatch(AiChannel channel, String agentId, String secret,
                                       String receiveId, ObjectNode msg) throws Exception {
        String token = accessToken(channel, secret);
        ObjectNode body = objectMapper.createObjectNode();
        body.put("agent_id", Long.parseLong(agentId));
        body.put("userid_list", receiveId == null ? "" : receiveId);
        body.set("msg", msg);
        String resp = restClient.post()
                .uri(baseUrl + "/topapi/message/corpconversation/asyncsend_v2?access_token=" + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body.toString())
                .retrieve()
                .body(String.class);
        JsonNode node = objectMapper.readTree(resp);
        int errcode = node.path("errcode").asInt(-1);
        if (errcode != 0) {
            return ChannelSendResult.fail("CHANNEL_SEND_FAILED",
                    "钉钉发送失败: errcode=" + errcode + " errmsg=" + node.path("errmsg").asText());
        }
        return ChannelSendResult.success();
    }

    private String accessToken(AiChannel channel, String secret) {
        TokenCache cached = tokenCache.get(channel.getChannelCode());
        if (cached != null && cached.expireAt() > System.currentTimeMillis()) {
            return cached.token();
        }
        try {
            String uri = baseUrl + "/gettoken?appkey=" + enc(channel.getAppId()) + "&appsecret=" + enc(secret);
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
            throw new IllegalStateException("钉钉 access_token 获取失败: " + e.getMessage());
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
