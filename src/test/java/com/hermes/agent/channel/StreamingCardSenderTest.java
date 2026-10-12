package com.hermes.agent.channel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hermes.agent.entity.AiChannel;
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

/**
 * 增量卡片全链路（离线）：起假钉钉（openapi token + interactiveCards + card/instances/update），
 * 验证 建卡→多次更新→收尾 的请求形态、全量覆盖语义与失败显式上报。
 */
class StreamingCardSenderTest {

    private HttpServer server;
    private String baseUrl;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private ChannelOutboundService outbound;

    /** 收到更新请求的次数（验证全量覆盖而非追加） */
    private final List<String> updates = new ArrayList<>();
    private final AtomicInteger tokenCalls = new AtomicInteger();
    private final AtomicInteger createCalls = new AtomicInteger();
    /** 测试开关：注入更新失败 */
    private volatile boolean failUpdate;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        outbound = new ChannelOutboundService(List.of(
                new DingTalkChannelAdapter(objectMapper, baseUrl, baseUrl) {
                    @Override
                    String resolveSecret(AiChannel channel) {
                        return "fake-secret";
                    }
                },
                new WecomChannelAdapter(objectMapper, baseUrl)));
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private AiChannel dingTalk(String config) {
        AiChannel c = new AiChannel();
        c.setChannelCode("dt-stream");
        c.setChannelType("DINGTALK");
        c.setName("钉钉增量");
        c.setAppId("app-key");
        c.setAppSecretRef("HERMES_NO_SUCH_ENV_REF_XYZ");
        c.setConfig(config);
        c.setEnabled(1);
        return c;
    }

    private void stubDingTalk() {
        server.createContext("/", ex -> {
            try {
                String path = ex.getRequestURI().getPath();
                String method = ex.getRequestMethod();
                String reqBody = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                byte[] payload;
                if (path.endsWith("/v1.0/oauth2/accessToken")) {
                    tokenCalls.incrementAndGet();
                    payload = "{\"accessToken\":\"tk-1\",\"expireIn\":7200}".getBytes(StandardCharsets.UTF_8);
                } else if (path.endsWith("/v1.0/im/interactiveCards")) {
                    createCalls.incrementAndGet();
                    payload = ("{\"result\":{\"outTrackId\":\"track-1\"}}").getBytes(StandardCharsets.UTF_8);
                } else if (path.endsWith("/update")) {
                    if (failUpdate) {
                        byte[] err = "{\"code\":\"invalid\"}".getBytes(StandardCharsets.UTF_8);
                        ex.sendResponseHeaders(500, err.length);
                        try (OutputStream os = ex.getResponseBody()) {
                            os.write(err);
                        }
                        ex.close();
                        return;
                    }
                    JsonNode node = objectMapper.readTree(reqBody);
                    updates.add(node.path("cardData").path("cardParamMap").path("content").asText());
                    payload = new byte[0];
                } else {
                    payload = "{}".getBytes(StandardCharsets.UTF_8);
                }
                ex.getResponseHeaders().add("Content-Type", "application/json");
                ex.sendResponseHeaders(200, payload.length);
                try (OutputStream os = ex.getResponseBody()) {
                    os.write(payload);
                }
                ex.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        server.start();
    }

    @Test
    void createUpdateFinishFlowPushesFullContentEachTime() {
        stubDingTalk();

        StreamingCardSender sender = outbound.streamCard(
                dingTalk("{\"cardTemplateId\":\"tpl-1\",\"robotCode\":\"robot-1\"}"), "user-1");
        boolean opened = sender.open(new StreamCard("标题", "tpl-1", "开始"));
        assertThat(opened).isTrue();
        assertThat(sender.handle().outTrackId()).isEqualTo("track-1");

        sender.append("第一段");
        sender.append("第二段");
        boolean done = sender.finish();

        assertThat(done).isTrue();
        assertThat(sender.failed()).isFalse();
        // 建卡内容在 create 接口里；update 共 3 次（2 次 append + 1 次 finish），每次全量覆盖
        assertThat(updates).containsExactly("第一段", "第一段第二段", "第一段第二段");
        assertThat(createCalls.get()).isEqualTo(1);
        // 同一渠道复用 accessToken
        assertThat(tokenCalls.get()).isEqualTo(1);
        assertThat(sender.content()).isEqualTo("第一段第二段");
        assertThat(sender.steps().stream().allMatch(StreamCard.Step::ok)).isTrue();
    }

    @Test
    void missingTemplateIdReportsConfigMissing() {
        stubDingTalk();
        StreamingCardSender sender = outbound.streamCard(dingTalk("{}"), "user-1");

        assertThat(sender.open(new StreamCard("标题", "", ""))).isFalse();
        assertThat(sender.failed()).isTrue();
        assertThat(sender.lastError()).contains("CHANNEL_CONFIG_MISSING");
        // 建卡失败 → 后续 append/finish 不应再发请求
        sender.append("x");
        sender.finish();
        assertThat(createCalls.get()).isZero();
    }

    @Test
    void unresolvedCredentialReportsCredential() {
        stubDingTalk();
        // 不复写 resolveSecret 的适配器：环境变量里没有该 ref → 报凭据未解析
        ChannelOutboundService strict = new ChannelOutboundService(List.of(
                new DingTalkChannelAdapter(objectMapper, baseUrl, baseUrl),
                new WecomChannelAdapter(objectMapper, baseUrl)));
        StreamingCardSender sender = strict.streamCard(
                dingTalk("{\"cardTemplateId\":\"tpl-1\"}"), "user-1");

        assertThat(sender.open(new StreamCard("标题", "tpl-1", ""))).isFalse();
        assertThat(sender.lastError()).contains("CHANNEL_CREDENTIAL_UNRESOLVED");
    }

    @Test
    void updateFailureStopsFurtherAppends() {
        stubDingTalk();

        StreamingCardSender sender = outbound.streamCard(
                dingTalk("{\"cardTemplateId\":\"tpl-1\"}"), "user-1");
        assertThat(sender.open(new StreamCard("标题", "tpl-1", ""))).isTrue();
        // 注入更新失败
        failUpdate = true;
        assertThat(sender.append("增量")).isFalse();
        assertThat(sender.failed()).isTrue();
        assertThat(sender.lastError()).contains("CHANNEL_STREAM_CARD_UPDATE_FAILED");
        assertThat(sender.finish()).isFalse();
    }

    @Test
    void wecomSaysUnsupportedHonestly() {
        stubDingTalk();
        AiChannel wecom = new AiChannel();
        wecom.setChannelCode("wc-stream");
        wecom.setChannelType("WECOM");
        wecom.setAppId("corp");
        wecom.setAppSecretRef("HERMES_NO_SUCH_ENV_REF_XYZ");
        wecom.setConfig("{\"agentId\":\"1\"}");
        wecom.setEnabled(1);

        ChannelAdapter.ChannelSendResult result =
                new WecomChannelAdapter(objectMapper, baseUrl).createStreamCard(wecom, "u1",
                        new StreamCard("标题", "tpl", ""));
        assertThat(result.ok()).isFalse();
        assertThat(result.errorCode()).isEqualTo("CHANNEL_STREAM_CARD_UNSUPPORTED");
    }
}
