package com.hermes.agent.channel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hermes.agent.approval.ApprovalSink;
import com.hermes.agent.command.CommandRouter;
import com.hermes.agent.entity.AiChannel;
import com.hermes.agent.entity.ApprovalRequest;
import com.hermes.agent.runtime.AgentRunRequest;
import com.hermes.agent.runtime.AgentRunResult;
import com.hermes.agent.runtime.AgentRuntime;
import com.hermes.agent.session.SessionManager;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 渠道入站管线（契约 §5.2 / agent-platform §9.2）：
 * @门控 → 配对检查（未绑定不进 Agent）→ 路由到单一基座 AgentRuntime → 出站回传。
 * 渠道来源的每一轮都记 ai_tool_call，channel 字段标注来源（审计不断链）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelEventService {

    private final ChannelService channelService;
    private final ChannelOutboundService outboundService;
    private final CommandRouter commandRouter;
    private final AgentRuntime agentRuntime;
    private final SessionManager sessionManager;
    private final ObjectMapper objectMapper;

    /**
     * 入站消息主入口，返回处理结论（connector 按结论决定是否重试）。
     */
    public Map<String, Object> handleInbound(String channelCode, InboundEvent event) {
        AiChannel channel = channelService.getChannel(channelCode);
        if (channel == null) {
            return Map.of("success", false, "errorCode", "CHANNEL_NOT_FOUND", "handled", false);
        }
        if (channel.getEnabled() == null || channel.getEnabled() == 0) {
            return Map.of("success", false, "errorCode", "CHANNEL_DISABLED", "handled", false);
        }

        // @门控：群里只响应被 @ 的消息
        boolean groupChat = event.getChatType() == null || "group".equalsIgnoreCase(event.getChatType());
        if (groupChat && !Boolean.TRUE.equals(event.getMentioned())) {
            return Map.of("success", true, "handled", false, "reason", "MENTION_GATE");
        }

        // 配对检查：未绑定用户不进 Agent，回配对提示
        Optional<Long> platformUser = channelService.resolvePlatformUser(channelCode, event.getChannelUserId());
        if (platformUser.isEmpty()) {
            outboundService.sendText(channel, event.getChannelUserId(),
                    "你的渠道身份尚未绑定平台账号，请先在平台完成配对后再提问（配对方式见平台说明）。");
            return Map.of("success", true, "handled", true, "reason", "PAIRING_PROMPT");
        }

        // 异步执行，webhook 快速返回
        new Thread(() -> process(channel, event, platformUser.get()),
                "channel-" + channelCode + "-" + event.getChannelUserId()).start();
        return Map.of("success", true, "handled", true, "reason", "ACCEPTED");
    }

    private void process(AiChannel channel, InboundEvent event, Long platformUserId) {
        String sessionId = "ch-" + channel.getChannelCode() + "-" + event.getChannelUserId();
        String traceId = UUID.randomUUID().toString();
        String receiveId = event.getChannelUserId();
        try {
            sessionManager.getSession(sessionId).orElseGet(() ->
                    sessionManager.createSession(sessionId, platformUserId, "assistant"));

            SessionManager.ChatMessage userMsg = new SessionManager.ChatMessage();
            userMsg.setMessageId(UUID.randomUUID().toString());
            userMsg.setRole("USER");
            userMsg.setContent(event.getMessage());
            userMsg.setTraceId(traceId);
            sessionManager.addMessage(sessionId, userMsg);

            String input = event.getMessage();
            CommandRouter.CommandResult cmdResult = commandRouter.route(input);
            if (cmdResult != null) {
                if (!cmdResult.isRouteToChat()) {
                    replyText(channel, receiveId, cmdResult.isSuccess()
                            ? cmdResult.getMessage()
                            : "指令执行失败: " + cmdResult.getMessage());
                    return;
                }
                input = cmdResult.getContext() + "\n\n用户消息：\n" + input;
            }

            AgentRunRequest runRequest = AgentRunRequest.builder()
                    .agentCode("assistant")
                    .userId(platformUserId)
                    .sessionId(sessionId)
                    .userInput(input)
                    .traceId(traceId)
                    .channel(channel.getChannelCode())
                    .build();

            AgentRunResult result = agentRuntime.stream(runRequest,
                    delta -> { /* 卡片流式更新为渠道适配器单元职责，骨架期先整段回传 */ },
                    toolEvent -> {
                        if (!toolEvent.isSuccess()) {
                            replyText(channel, receiveId, "工具 " + toolEvent.getToolCode()
                                    + " 执行失败: " + toolEvent.getErrorMessage());
                        }
                    },
                    channelApprovalSink(channel, receiveId));

            SessionManager.ChatMessage assistantMsg = new SessionManager.ChatMessage();
            assistantMsg.setMessageId(UUID.randomUUID().toString());
            assistantMsg.setRole("ASSISTANT");
            assistantMsg.setContent(result.getReply());
            assistantMsg.setTraceId(traceId);
            sessionManager.addMessage(sessionId, assistantMsg);

            replyText(channel, receiveId, result.getReply() == null ? "" : result.getReply());
        } catch (Exception e) {
            log.error("渠道入站处理失败: channel={}, user={}: {}",
                    channel.getChannelCode(), receiveId, e.getMessage(), e);
            replyText(channel, receiveId, "处理失败: " + e.getMessage());
        }
    }

    private void replyText(AiChannel channel, String receiveId, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        outboundService.sendText(channel, receiveId, text);
    }

    /** 审批卡片走渠道出站（出站卡片回传） */
    private ApprovalSink channelApprovalSink(AiChannel channel, String receiveId) {
        return new ApprovalSink() {
            @Override
            public void onApprovalRequest(ApprovalRequest card) {
                outboundService.sendCard(channel, receiveId, approvalCardJson(card));
            }

            @Override
            public void onApprovalCancel(String requestId, String reason) {
                outboundService.sendText(channel, receiveId, "审批单已撤回: " + requestId + "（" + reason + "）");
            }
        };
    }

    private String approvalCardJson(ApprovalRequest card) {
        try {
            Map<String, Object> cardMap = Map.of(
                    "header", Map.of("template", "blue", "title",
                            Map.of("tag", "plain_text", "content", "工具审批请求")),
                    "elements", java.util.List.of(
                            Map.of("tag", "div", "text", Map.of("tag", "lark_md",
                                    "content", "**工具**: " + card.getToolCode()
                                            + "\n**参数**: " + card.getArguments()
                                            + "\n**审批单号**: " + card.getRequestId()
                                            + "\n请在平台侧应答该审批单。"))));
            return objectMapper.writeValueAsString(cardMap);
        } catch (Exception e) {
            return "{}";
        }
    }

    @Data
    public static class InboundEvent {
        private String messageId;
        private String channelUserId;
        /** group / dm，缺省 group */
        private String chatType;
        private Boolean mentioned;
        private String message;
    }
}
