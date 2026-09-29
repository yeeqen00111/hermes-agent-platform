package com.hermes.agent.api;

import com.hermes.agent.command.CommandRouter;
import com.hermes.agent.dto.SSEEvent;
import com.hermes.agent.common.enums.SSEEventType;
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

        // 创建或获取会话
        sessionManager.getSession(sessionId).orElseGet(() ->
                sessionManager.createSession(sessionId, userId != null ? userId : 0L, request.getAgentCode()));

        // 检查是否是指令
        CommandRouter.CommandResult cmdResult = commandRouter.route(request.getMessage());
        if (cmdResult != null) {
            // 是指令，直接返回结果
            return handleCommandResponse(sessionId, cmdResult);
        }

        // 普通对话，流式返回
        return handleChatStream(sessionId, request, traceId);
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
     * 停止生成
     */
    @PostMapping("/sessions/{sessionId}/interrupt")
    public Map<String, Object> stopGeneration(@PathVariable String sessionId) {
        sessionManager.stopSession(sessionId);
        return Map.of("success", true, "message", "已停止生成");
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
     * 处理聊天流（Mock实现）
     */
    private SseEmitter handleChatStream(String sessionId, ChatRequest request, String traceId) {
        SseEmitter emitter = new SseEmitter(60000L);

        // 异步处理（实际应该交给Planner和LLM Gateway）
        new Thread(() -> {
            try {
                // 模拟思考过程
                emitter.send(SseEmitter.event()
                        .name(SSEEventType.REASONING_DELTA.getType())
                        .data("正在分析问题..."));

                Thread.sleep(500);

                // 模拟工具调用
                emitter.send(SseEmitter.event()
                        .name(SSEEventType.TOOL_START.getType())
                        .data(Map.of("tool", "log.search")));

                Thread.sleep(300);

                emitter.send(SseEmitter.event()
                        .name(SSEEventType.TOOL_COMPLETE.getType())
                        .data(Map.of("tool", "log.search", "success", true)));

                // 模拟回答
                String response = "这是Mock回答。您问的是：" + request.getMessage();
                for (int i = 0; i < response.length(); i += 5) {
                    int end = Math.min(i + 5, response.length());
                    emitter.send(SseEmitter.event()
                            .name(SSEEventType.MESSAGE_DELTA.getType())
                            .data(response.substring(i, end)));
                    Thread.sleep(50);
                }

                // 发送引用
                emitter.send(SseEmitter.event()
                        .name(SSEEventType.CITATIONS.getType())
                        .data(List.of(Map.of(
                                "kind", "log",
                                "source", "mock-source",
                                "title", "Mock Citation",
                                "locator", "mock-001"
                        ))));

                // 完成
                emitter.send(SseEmitter.event()
                        .name(SSEEventType.MESSAGE_COMPLETE.getType())
                        .data(Map.of("sessionId", sessionId)));

                emitter.complete();

                // 保存消息到会话
                SessionManager.ChatMessage userMsg = new SessionManager.ChatMessage();
                userMsg.setMessageId(UUID.randomUUID().toString());
                userMsg.setRole("USER");
                userMsg.setContent(request.getMessage());
                userMsg.setTraceId(traceId);
                sessionManager.addMessage(sessionId, userMsg);

            } catch (IOException | InterruptedException e) {
                emitter.completeWithError(e);
            }
        }).start();

        return emitter;
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
