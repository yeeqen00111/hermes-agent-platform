package com.hermes.agent.channel;

import com.hermes.agent.entity.AiChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 渠道出站分发：按 channel_type 找适配器，失败显式上报不吞错。
 * 通知路径（ai_notify_*）与对话路径（本服务）分开，共用凭据、不共享连接（契约 §5.1）。
 */
@Slf4j
@Service
public class ChannelOutboundService {

    private final Map<String, ChannelAdapter> adapters = new HashMap<>();

    public ChannelOutboundService(List<ChannelAdapter> adapterList) {
        adapterList.forEach(a -> adapters.put(a.type(), a));
    }

    public ChannelAdapter.ChannelSendResult sendText(AiChannel channel, String receiveId, String text) {
        return send(channel, receiveId, text, null);
    }

    public ChannelAdapter.ChannelSendResult sendCard(AiChannel channel, String receiveId, String cardJson) {
        return send(channel, receiveId, null, cardJson);
    }

    /** 增量卡片门面：建卡 + 多次增量更新 + 收尾（Agent 流式回答边生成边推） */
    public StreamingCardSender streamCard(AiChannel channel, String receiveId) {
        return new StreamingCardSender(this, channel, receiveId);
    }

    ChannelAdapter.ChannelSendResult createStreamCard(AiChannel channel, String receiveId,
                                                      StreamCard card) {
        ChannelAdapter adapter = adapters.get(channel.getChannelType());
        if (adapter == null) {
            return ChannelAdapter.ChannelSendResult.fail("CHANNEL_TYPE_UNKNOWN",
                    "未知渠道类型: " + channel.getChannelType());
        }
        return adapter.createStreamCard(channel, receiveId, card);
    }

    ChannelAdapter.ChannelSendResult updateStreamCard(AiChannel channel,
                                                      StreamCard.CardHandle handle, String content) {
        ChannelAdapter adapter = adapters.get(channel.getChannelType());
        if (adapter == null) {
            return ChannelAdapter.ChannelSendResult.fail("CHANNEL_TYPE_UNKNOWN",
                    "未知渠道类型: " + channel.getChannelType());
        }
        return adapter.updateStreamCard(channel, handle, content);
    }

    private ChannelAdapter.ChannelSendResult send(AiChannel channel, String receiveId, String text, String cardJson) {
        ChannelAdapter adapter = adapters.get(channel.getChannelType());
        if (adapter == null) {
            return ChannelAdapter.ChannelSendResult.fail("CHANNEL_TYPE_UNKNOWN",
                    "未知渠道类型: " + channel.getChannelType());
        }
        ChannelAdapter.ChannelSendResult result = text != null
                ? adapter.sendText(channel, receiveId, text)
                : adapter.sendCard(channel, receiveId, cardJson);
        if (!result.ok()) {
            log.warn("渠道出站失败: channel={}, receiveId={}, code={}: {}",
                    channel.getChannelCode(), receiveId, result.errorCode(), result.message());
        }
        return result;
    }
}
