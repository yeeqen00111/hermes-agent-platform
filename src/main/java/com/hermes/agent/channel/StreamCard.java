package com.hermes.agent.channel;

import com.hermes.agent.entity.AiChannel;

import java.util.Objects;

/**
 * 增量卡片（流式卡片）的值对象。
 *
 * <p>用于把 Agent 的流式回答边生成边推到已发送的卡片上，替代「等整段生成完再发一条长文本」。
 *  Semantics：先 {@code createStreamCard} 建卡拿到句柄，再多次 {@code updateStreamCard} 覆盖正文，
 *  最后一次置为终态。不同渠道的卡片形态不同（钉钉 AI 卡片可更新；企微无更新接口）。
 */
public final class StreamCard {

    /** 卡片标题 */
    private final String title;

    /** 渠道侧卡片模板编码（钉钉 cardTemplateId） */
    private final String templateId;

    /** 建卡时的初始正文（markdown） */
    private final String initialContent;

    public StreamCard(String title, String templateId, String initialContent) {
        this.title = title;
        this.templateId = templateId;
        this.initialContent = initialContent;
    }

    public String title() {
        return title;
    }

    public String templateId() {
        return templateId;
    }

    public String initialContent() {
        return initialContent == null ? "" : initialContent;
    }

    /** 建卡成功后返回的句柄：渠道 + 卡片实例标识 + 接收方 */
    public record CardHandle(String channelCode, String outTrackId, String receiveId) {
        public CardHandle {
            Objects.requireNonNull(outTrackId, "outTrackId");
        }
    }

    /** 上次发送的步骤轨迹（调试与前端展示用） */
    public record Step(String step, String error) {
        static Step of(String step, String error) {
            return new Step(step, error);
        }

        public boolean ok() {
            return error == null;
        }
    }
}
