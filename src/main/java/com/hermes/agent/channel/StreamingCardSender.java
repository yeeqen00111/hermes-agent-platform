package com.hermes.agent.channel;

import com.hermes.agent.entity.AiChannel;

import java.util.ArrayList;
import java.util.List;

/**
 * 增量卡片发送门面：把「建卡 → 多次增量更新 → 收尾」封装成一个可顺序调用的会话。
 *
 * <p>钉钉卡片更新是**全量覆盖 content**（不是追加），所以这里累积已推送正文，
 * 每次 {@link #append} 都把完整正文推上去——Agent 边生成、用户边看到累积结果。
 * 任一步失败即置 {@code failed}，调用方应停止后续增量（避免刷屏）。
 */
public class StreamingCardSender {

    private final ChannelOutboundService outbound;
    private final AiChannel channel;
    private final String receiveId;
    private final StringBuilder content = new StringBuilder();
    private final List<StreamCard.Step> steps = new ArrayList<>();

    private StreamCard.CardHandle handle;
    private boolean failed;
    private boolean finished;

    StreamingCardSender(ChannelOutboundService outbound, AiChannel channel, String receiveId) {
        this.outbound = outbound;
        this.channel = channel;
        this.receiveId = receiveId;
    }

    /** 建卡（带初始正文）。失败返回 false，原因见 {@link #lastError()}。 */
    public boolean open(StreamCard card) {
        ChannelAdapter.ChannelSendResult result =
                outbound.createStreamCard(channel, receiveId, card);
        if (result.ok() && result.handle() != null) {
            handle = new StreamCard.CardHandle(channel.getChannelCode(), result.handle(), receiveId);
            steps.add(StreamCard.Step.of("create", null));
            return true;
        }
        fail(result, "create");
        return false;
    }

    /** 追加一段增量并推送（全量覆盖）。 */
    public boolean append(String delta) {
        if (finished || failed || handle == null) {
            return false;
        }
        if (delta != null) {
            content.append(delta);
        }
        return update("append");
    }

    /** 收尾：置终态（content 为空则只置终态）。已失败则返回 false（未成功收尾）。 */
    public boolean finish() {
        if (failed || handle == null) {
            return false;
        }
        if (finished) {
            return true;
        }
        boolean ok = update("finish");
        finished = true;
        return ok;
    }

    private boolean update(String step) {
        ChannelAdapter.ChannelSendResult result =
                outbound.updateStreamCard(channel, handle, content.toString());
        if (result.ok()) {
            steps.add(StreamCard.Step.of(step, null));
            return true;
        }
        fail(result, step);
        return false;
    }

    private void fail(ChannelAdapter.ChannelSendResult result, String step) {
        failed = true;
        steps.add(StreamCard.Step.of(step, result.errorCode() + ": " + result.message()));
    }

    public boolean failed() {
        return failed;
    }

    public boolean finished() {
        return finished;
    }

    /** 当前累积正文（失败兜底可直接降级为文本发送） */
    public String content() {
        return content.toString();
    }

    public StreamCard.CardHandle handle() {
        return handle;
    }

    public String lastError() {
        for (int i = steps.size() - 1; i >= 0; i--) {
            if (steps.get(i).error() != null) {
                return steps.get(i).step() + " → " + steps.get(i).error();
            }
        }
        return null;
    }

    public List<StreamCard.Step> steps() {
        return List.copyOf(steps);
    }
}
