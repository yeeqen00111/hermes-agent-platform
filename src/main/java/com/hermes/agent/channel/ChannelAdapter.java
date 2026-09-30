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

    record ChannelSendResult(boolean ok, String errorCode, String message) {
        public static ChannelSendResult success() {
            return new ChannelSendResult(true, null, null);
        }

        public static ChannelSendResult fail(String errorCode, String message) {
            return new ChannelSendResult(false, errorCode, message);
        }
    }
}
