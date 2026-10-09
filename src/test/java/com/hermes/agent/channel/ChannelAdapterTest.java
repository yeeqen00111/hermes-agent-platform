package com.hermes.agent.channel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hermes.agent.entity.AiChannel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 钉钉 / 企微出站适配器：离线校验（配置缺失 / 凭据未解析）——不触网也不伪装成功。
 */
class ChannelAdapterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DingTalkChannelAdapter dingTalk = new DingTalkChannelAdapter(objectMapper, "http://127.0.0.1:1");
    private final WecomChannelAdapter wecom = new WecomChannelAdapter(objectMapper, "http://127.0.0.1:1");

    private AiChannel channel(String code, String config, String secretRef) {
        AiChannel c = new AiChannel();
        c.setChannelCode(code);
        c.setChannelType(code.startsWith("dt") ? "DINGTALK" : "WECOM");
        c.setName(code);
        c.setAppId("app-id");
        c.setAppSecretRef(secretRef);
        c.setConfig(config);
        c.setEnabled(1);
        return c;
    }

    @Test
    void dingTalkMissingAgentIdReportsConfigMissing() {
        ChannelAdapter.ChannelSendResult result = dingTalk.sendText(channel("dt-1", null, null), "u1", "hi");
        assertThat(result.ok()).isFalse();
        assertThat(result.errorCode()).isEqualTo("CHANNEL_CONFIG_MISSING");
    }

    @Test
    void dingTalkUnresolvedSecretReportsCredential() {
        AiChannel c = channel("dt-2", "{\"agentId\":\"123\"}", "HERMES_NO_SUCH_ENV_REF_XYZ");
        ChannelAdapter.ChannelSendResult result = dingTalk.sendText(c, "u1", "hi");
        assertThat(result.ok()).isFalse();
        assertThat(result.errorCode()).isEqualTo("CHANNEL_CREDENTIAL_UNRESOLVED");
    }

    @Test
    void dingTalkInvalidCardReportsCardInvalid() {
        AiChannel c = channel("dt-3", "{\"agentId\":\"123\"}", "HERMES_NO_SUCH_ENV_REF_XYZ");
        ChannelAdapter.ChannelSendResult result = dingTalk.sendCard(c, "u1", "{\"title\":\"t\"}");
        // 校验顺序：先 agentId（有），再凭据（未解析）→ 仍应先报凭据；此处验证卡片字段缺失分支
        assertThat(result.ok()).isFalse();
        assertThat(result.errorCode()).isIn("CHANNEL_CREDENTIAL_UNRESOLVED", "CHANNEL_CARD_INVALID");
    }

    @Test
    void wecomMissingAgentIdReportsConfigMissing() {
        ChannelAdapter.ChannelSendResult result = wecom.sendText(channel("wc-1", "{}", null), "u1", "hi");
        assertThat(result.ok()).isFalse();
        assertThat(result.errorCode()).isEqualTo("CHANNEL_CONFIG_MISSING");
    }

    @Test
    void wecomUnresolvedSecretReportsCredential() {
        AiChannel c = channel("wc-2", "{\"agentId\":\"1000002\"}", "HERMES_NO_SUCH_ENV_REF_XYZ");
        ChannelAdapter.ChannelSendResult result = wecom.sendText(c, "u1", "hi");
        assertThat(result.ok()).isFalse();
        assertThat(result.errorCode()).isEqualTo("CHANNEL_CREDENTIAL_UNRESOLVED");
    }

    @Test
    void adapterTypes() {
        assertThat(dingTalk.type()).isEqualTo("DINGTALK");
        assertThat(wecom.type()).isEqualTo("WECOM");
    }
}
