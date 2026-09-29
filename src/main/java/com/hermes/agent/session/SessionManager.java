package com.hermes.agent.session;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话管理器
 */
@Slf4j
@Component
public class SessionManager {

    private final Map<String, ChatSession> sessions = new ConcurrentHashMap<>();

    /**
     * 创建新会话
     */
    public ChatSession createSession(String sessionId, Long userId, String agentCode) {
        ChatSession session = new ChatSession();
        session.setSessionId(sessionId);
        session.setUserId(userId);
        session.setAgentCode(agentCode);
        session.setStatus("ACTIVE");
        session.setCreateTime(LocalDateTime.now());
        session.setMessages(new ArrayList<>());

        sessions.put(sessionId, session);
        log.info("创建会话: {}, 用户: {}, Agent: {}", sessionId, userId, agentCode);
        return session;
    }

    /**
     * 获取会话
     */
    public Optional<ChatSession> getSession(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    /**
     * 添加消息
     */
    public void addMessage(String sessionId, ChatMessage message) {
        ChatSession session = sessions.get(sessionId);
        if (session != null) {
            session.getMessages().add(message);
            session.setLastMessageTime(LocalDateTime.now());
        }
    }

    /**
     * 获取会话历史
     */
    public List<ChatMessage> getHistory(String sessionId, int limit) {
        ChatSession session = sessions.get(sessionId);
        if (session == null) {
            return Collections.emptyList();
        }

        List<ChatMessage> messages = session.getMessages();
        int fromIndex = Math.max(0, messages.size() - limit);
        return messages.subList(fromIndex, messages.size());
    }

    /**
     * 停止会话（中断生成）
     */
    public void stopSession(String sessionId) {
        ChatSession session = sessions.get(sessionId);
        if (session != null) {
            session.setStatus("STOPPED");
            log.info("停止会话: {}", sessionId);
        }
    }

    /**
     * 列出用户的会话
     */
    public List<ChatSession> listSessions(Long userId, int page, int size) {
        return sessions.values().stream()
                .filter(s -> s.getUserId().equals(userId))
                .sorted((a, b) -> b.getLastMessageTime().compareTo(a.getLastMessageTime()))
                .skip((long) (page - 1) * size)
                .limit(size)
                .toList();
    }

    @Data
    public static class ChatSession {
        private String sessionId;
        private Long userId;
        private String agentCode;
        private Integer agentVersion;
        private String title;
        private String channel;
        private String status;
        private LocalDateTime createTime;
        private LocalDateTime lastMessageTime;
        private List<ChatMessage> messages;
    }

    @Data
    public static class ChatMessage {
        private String messageId;
        private String role; // USER/ASSISTANT/SYSTEM
        private String content;
        private Object toolCalls;
        private Object citations;
        private String traceId;
        private LocalDateTime createTime;
    }
}
