package com.hermes.agent.channel;

import com.hermes.agent.entity.AiChannel;
import org.springframework.stereotype.Component;

/**
 * 钉钉渠道对接位：适配器骨架，未实现（显式报错，不伪装成功）
 */
@Component
public class DingTalkChannelAdapter implements ChannelAdapter {

    @Override
    public String type() {
        return "DINGTALK";
    }

    @Override
    public ChannelSendResult sendText(AiChannel channel, String receiveId, String text) {
        return ChannelSendResult.fail("CHANNEL_NOT_IMPLEMENTED", "钉钉渠道适配器未实现（对接位预留）");
    }

    @Override
    public ChannelSendResult sendCard(AiChannel channel, String receiveId, String cardJson) {
        return ChannelSendResult.fail("CHANNEL_NOT_IMPLEMENTED", "钉钉渠道适配器未实现（对接位预留）");
    }
}
