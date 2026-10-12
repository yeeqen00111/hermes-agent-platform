package com.hermes.agent.channel;

import com.hermes.agent.entity.AiChannel;

/**
 * 渠道出站适配器：通知路径与对话路径分开实现（契约 §5.1 规矩2）。
 * 出站走适配器已建立/管理的连接，失败返回显式错误码，不伪装成功。
 */
public interface ChannelAdapter {

    /** FEISHU / DINGTALK / WECOM */
    String type();

    ChannelSendResult sendText(AiChannel channel, String receiveId, String text);

    ChannelSendResult sendCard(AiChannel channel, String receiveId, String cardJson);

    /**
     * 增量卡片：建卡并返回句柄。默认不支持（企微等无更新接口）。
     */
    default ChannelSendResult createStreamCard(AiChannel channel, String receiveId, StreamCard card) {
        return ChannelSendResult.fail("CHANNEL_STREAM_CARD_UNSUPPORTED",
                "渠道类型 " + type() + " 不支持增量卡片");
    }

    /**
     * 增量卡片：更新已建卡片的正文（全量覆盖 content）。
     */
    default ChannelSendResult updateStreamCard(AiChannel channel, StreamCard.CardHandle handle,
                                               String content) {
        return ChannelSendResult.fail("CHANNEL_STREAM_CARD_UNSUPPORTED",
                "渠道类型 " + type() + " 不支持增量卡片");
    }

    /**
     * 增量卡片：收尾（终态）。content 为空表示只置终态不改正文。
     */
    default ChannelSendResult finishStreamCard(AiChannel channel, StreamCard.CardHandle handle,
                                               String content) {
        return ChannelSendResult.fail("CHANNEL_STREAM_CARD_UNSUPPORTED",
                "渠道类型 " + type() + " 不支持增量卡片");
    }

    /**
     * 出站结果。{@code handle} 仅在增量卡片建卡成功时非空（钉钉 outTrackId），其余场景为空。
     */
    record ChannelSendResult(boolean ok, String errorCode, String message, String handle) {

        public static ChannelSendResult success() {
            return new ChannelSendResult(true, null, null, null);
        }

        public static ChannelSendResult success(String handle) {
            return new ChannelSendResult(true, null, null, handle);
        }

        public static ChannelSendResult fail(String errorCode, String message) {
            return new ChannelSendResult(false, errorCode, message, null);
        }
    }
}
