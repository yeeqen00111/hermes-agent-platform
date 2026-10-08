package com.hermes.agent.api;

import com.hermes.agent.approval.ApprovalService;
import com.hermes.agent.approval.ApprovalSink;
import com.hermes.agent.command.CommandRouter;
import com.hermes.agent.dto.SSEEvent;
import com.hermes.agent.common.enums.SSEEventType;
import com.hermes.agent.entity.ApprovalRequest;
import com.hermes.agent.runtime.AgentRunRequest;
import com.hermes.agent.runtime.AgentRunResult;
import com.hermes.agent.runtime.AgentRuntime;
import com.hermes.agent.session.ChatStopService;
import com.hermes.agent.session.SessionManager;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Chat API 控制器
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ChatController {

    private final SessionManager sessionManager;
    private final CommandRouter commandRouter;
    private final AgentRuntime agentRuntime;
    private final ApprovalService approvalService;
    private final ChatStopService chatStopService;

    /**
     * 发送消息（SSE流式返回）
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody ChatRequest request,
                           @RequestHeader(value = "X-Actor-User-Id", required = false) Long userId,
                           @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {

        String sessionId = request.getSessionId();
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = UUID.randomUUID().toString();
        }
        final String sid = sessionId;
        // 调用链ID必填（ai_tool_call.trace_id NOT NULL），未传则生成
        final String effectiveTraceId = (traceId == null || traceId.isBlank())
                ? UUID.randomUUID().toString() : traceId;

        // 创建或获取会话；停止过的会话再次发消息时自动重新激活
        SessionManager.ChatSession session = sessionManager.getSession(sid).orElseGet(() ->
                sessionManager.createSession(sid, userId != null ? userId : 0L, request.getAgentCode()));
        if ("STOPPED".equals(session.getStatus())) {
            sessionManager.markActive(sid);
        }

        // 检查是否是指令
        CommandRouter.CommandResult cmdResult = commandRouter.route(request.getMessage(),
                new CommandRouter.CommandContext(sid, request.getAgentCode(), userId));
        if (cmdResult != null) {
            if (cmdResult.isRouteToChat()) {
                // 技能/捆绑包指令：正文拼到本轮用户消息前，走正常对话（§7.2）
                String augmented = cmdResult.getContext() + "\n\n用户消息：\n" + request.getMessage();
                return handleChatStream(sid, request, effectiveTraceId, userId, augmented);
            }
            // 是指令，直接返回结果
            return handleCommandResponse(sid, cmdResult);
        }

        // 普通对话，流式返回
        return handleChatStream(sid, request, effectiveTraceId, userId, request.getMessage());
    }

    /**
     * 获取会话列表
     */
    @GetMapping("/sessions")
    public List<SessionManager.ChatSession> listSessions(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return sessionManager.listSessions(userId, page, size);
    }

    /**
     * 获取历史消息
     */
    @GetMapping("/sessions/{sessionId}/messages")
    public List<SessionManager.ChatMessage> getMessages(
            @PathVariable String sessionId,
            @RequestParam(defaultValue = "50") int limit) {
        return sessionManager.getHistory(sessionId, limit);
    }

    /**
     * 停止生成（#54：与 /stop 同一链路——取消在跑轮+取消待审卡+库置 STOPPED）
     */
    @PostMapping("/sessions/{sessionId}/interrupt")
    public Map<String, Object> stopGeneration(@PathVariable String sessionId) {
        boolean hadRunning = chatStopService.stopRun(sessionId, "会话被用户中断");
        return Map.of("success", true, "message", "已停止生成", "hadRunning", hadRunning);
    }

    /**
     * 反馈
     */
    @PostMapping("/sessions/{sessionId}/feedback")
    public Map<String, Object> feedback(@PathVariable String sessionId,
                                        @RequestBody FeedbackRequest request) {
        // TODO: 实现反馈逻辑
        log.info("收到反馈: sessionId={}, rating={}", sessionId, request.getRating());
        return Map.of("success", true, "message", "感谢反馈");
    }

    /**
     * 处理指令响应
     */
    private SseEmitter handleCommandResponse(String sessionId, CommandRouter.CommandResult result) {
        SseEmitter emitter = new SseEmitter(30000L);

        try {
            if (result.isSuccess()) {
                emitter.send(SseEmitter.event()
                        .name(SSEEventType.MESSAGE_DELTA.getType())
                        .data(result.getMessage()));
            } else {
                emitter.send(SseEmitter.event()
                        .name(SSEEventType.CHAT_ERROR.getType())
                        .data(Map.of("errorCode", result.getErrorCode(), "message", result.getMessage())));
            }
            emitter.send(SseEmitter.event()
                    .name(SSEEventType.MESSAGE_COMPLETE.getType())
                    .data(Map.of("sessionId", sessionId)));
            emitter.complete();
        } catch (IOException e) {
            emitter.completeWithError(e);
        }

        return emitter;
    }

    /**
     * 处理聊天流：交给单一基座 AgentRuntime（身份包 + LLM_DRIVEN/FIXED_FLOW）
     *
     * @param effectiveInput 实际进模型的消息（技能指令会带并进的正文）；会话历史仍存用户原始消息
     */
    private SseEmitter handleChatStream(String sessionId, ChatRequest request, String traceId,
                                        Long userId, String effectiveInput) {
        // 审批可能阻塞整轮，SSE 存活时间必须大于审批超时
        SseEmitter emitter = new SseEmitter((approvalService.getTimeoutSeconds() + 60) * 1000L);

        SessionManager.ChatMessage userMsg = new SessionManager.ChatMessage();
        userMsg.setMessageId(UUID.randomUUID().toString());
        userMsg.setRole("USER");
        userMsg.setContent(request.getMessage());
        userMsg.setTraceId(traceId);
        sessionManager.addMessage(sessionId, userMsg);

        new Thread(() -> {
            try {
                AgentRunRequest runRequest = AgentRunRequest.builder()
                        .agentCode(request.getAgentCode())
                        .userId(userId != null ? userId : 0L)
                        .sessionId(sessionId)
                        .userInput(effectiveInput)
                        .traceId(traceId)
                        .channel("chat")
                        .build();

                AgentRunResult result = agentRuntime.stream(runRequest,
                        delta -> sendQuietly(emitter, SSEEventType.MESSAGE_DELTA, delta),
                        event -> sendQuietly(emitter,
                                event.isSuccess() ? SSEEventType.TOOL_COMPLETE : SSEEventType.TOOL_START,
                                Map.of("tool", event.getToolCode(),
                                        "success", event.isSuccess(),
                                        "error", event.getErrorMessage() == null ? "" : event.getErrorMessage())),
                        approvalSink(emitter));

                SessionManager.ChatMessage assistantMsg = new SessionManager.ChatMessage();
                assistantMsg.setMessageId(UUID.randomUUID().toString());
                assistantMsg.setRole("ASSISTANT");
                assistantMsg.setContent(result.getReply());
                assistantMsg.setTraceId(traceId);
                sessionManager.addMessage(sessionId, assistantMsg);

                emitter.send(SseEmitter.event()
                        .name(SSEEventType.MESSAGE_COMPLETE.getType())
                        .data(Map.of("sessionId", sessionId,
                                "toolEvents", result.getToolEvents(),
                                "fixedFlow", result.isFixedFlow(),
                                "interrupted", result.isInterrupted())));
                emitter.complete();
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
        }).start();

        return emitter;
    }

    /**
     * 审批卡片走同一条 SSE 通道，卡片绑定会话（审批是会话级的）
     */
    private ApprovalSink approvalSink(SseEmitter emitter) {
        return new ApprovalSink() {
            @Override
            public void onApprovalRequest(ApprovalRequest card) {
                sendQuietly(emitter, SSEEventType.APPROVAL_REQUEST, Map.of(
                        "requestId", card.getRequestId(),
                        "sessionId", nullToEmpty(card.getSessionId()),
                        "tool", nullToEmpty(card.getToolCode()),
                        "toolName", nullToEmpty(card.getToolName()),
                        "safetyLevel", nullToEmpty(card.getSafetyLevel()),
                        "arguments", nullToEmpty(card.getArguments()),
                        "expireTime", String.valueOf(card.getExpireTime())));
            }

            @Override
            public void onApprovalCancel(String requestId, String reason) {
                sendQuietly(emitter, SSEEventType.APPROVAL_CANCEL, Map.of(
                        "requestId", nullToEmpty(requestId),
                        "reason", nullToEmpty(reason)));
            }
        };
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private void sendQuietly(SseEmitter emitter, SSEEventType type, Object data) {
        try {
            emitter.send(SseEmitter.event().name(type.getType()).data(data));
        } catch (IOException e) {
            log.debug("SSE发送失败: {}", e.getMessage());
        }
    }

    @Data
    public static class ChatRequest {
        private String sessionId;
        private String agentCode;
        private String message;
    }

    @Data
    public static class FeedbackRequest {
        private String rating; // thumbs_up / thumbs_down
        private String comment;
    }
}
