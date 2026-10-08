package com.hermes.agent.session;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hermes.agent.entity.AiChatMessage;
import com.hermes.agent.entity.AiChatSession;
import com.hermes.agent.mapper.AiChatMessageMapper;
import com.hermes.agent.mapper.AiChatSessionMapper;
import com.hermes.agent.persona.SessionVersionResolver;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 会话管理器：库为权威（ai_chat_session / ai_chat_message），契约 §7.1。
 * 重启不丢历史，多轮上下文从库里回读。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionManager {

    private final AiChatSessionMapper sessionMapper;
    private final AiChatMessageMapper messageMapper;
    private final ObjectMapper objectMapper;
    private final SessionVersionResolver versionResolver;

    /**
     * 创建新会话（幂等：已存在则直接返回）
     */
    public ChatSession createSession(String sessionId, Long userId, String agentCode) {
        AiChatSession row = new AiChatSession();
        row.setSessionId(sessionId);
        row.setUserId(userId);
        row.setAgentCode(agentCode);
        row.setAgentVersion(versionResolver.resolve(agentCode));
        row.setStatus("ACTIVE");
        row.setLastMessageTime(LocalDateTime.now());
        try {
            sessionMapper.insert(row);
            log.info("创建会话: {}, 用户: {}, Agent: {}", sessionId, userId, agentCode);
        } catch (DuplicateKeyException e) {
            log.info("会话已存在，复用: {}", sessionId);
            return getSession(sessionId).orElseGet(() -> toChatSession(row));
        }
        return toChatSession(row);
    }

    /**
     * 获取会话
     */
    public Optional<ChatSession> getSession(String sessionId) {
        return Optional.ofNullable(sessionMapper.selectOne(new LambdaQueryWrapper<AiChatSession>()
                .eq(AiChatSession::getSessionId, sessionId)))
                .map(this::toChatSession);
    }

    /**
     * 会话级模型覆盖（/model 指令）：override 为空串/null 表示清除
     */
    public void setModelOverride(String sessionId, String override) {
        sessionMapper.update(null, new LambdaUpdateWrapper<AiChatSession>()
                .eq(AiChatSession::getSessionId, sessionId)
                .set(AiChatSession::getModelOverride, override == null || override.isBlank() ? null : override));
    }

    /**
     * 添加消息（落库 + 刷新会话最后消息时间）
     */
    public void addMessage(String sessionId, ChatMessage message) {
        AiChatMessage row = new AiChatMessage();
        row.setSessionId(sessionId);
        row.setMessageId(message.getMessageId());
        row.setRole(message.getRole());
        row.setContent(message.getContent());
        row.setToolCalls(toJson(message.getToolCalls()));
        row.setCitations(toJson(message.getCitations()));
        row.setTraceId(message.getTraceId());
        row.setCreateTime(message.getCreateTime() == null ? LocalDateTime.now() : message.getCreateTime());
        messageMapper.insert(row);

        sessionMapper.update(null, new LambdaUpdateWrapper<AiChatSession>()
                .eq(AiChatSession::getSessionId, sessionId)
                .set(AiChatSession::getLastMessageTime, LocalDateTime.now()));
    }

    /**
     * 获取会话历史（最后 limit 条，按时间正序返回）
     */
    public List<ChatMessage> getHistory(String sessionId, int limit) {
        List<AiChatMessage> rows = messageMapper.selectList(new LambdaQueryWrapper<AiChatMessage>()
                .eq(AiChatMessage::getSessionId, sessionId)
                .orderByDesc(AiChatMessage::getId)
                .last("LIMIT " + Math.max(1, limit)));
        List<ChatMessage> messages = new ArrayList<>(rows.size());
        for (int i = rows.size() - 1; i >= 0; i--) {
            messages.add(toChatMessage(rows.get(i)));
        }
        return messages;
    }

    /**
     * 停止会话（中断生成）
     */
    public void stopSession(String sessionId) {
        int updated = sessionMapper.update(null, new LambdaUpdateWrapper<AiChatSession>()
                .eq(AiChatSession::getSessionId, sessionId)
                .set(AiChatSession::getStatus, "STOPPED"));
        if (updated > 0) {
            log.info("停止会话: {}", sessionId);
        }
    }

    /**
     * 会话重新激活（停止后的会话再次发消息时恢复 ACTIVE）
     */
    public void markActive(String sessionId) {
        sessionMapper.update(null, new LambdaUpdateWrapper<AiChatSession>()
                .eq(AiChatSession::getSessionId, sessionId)
                .set(AiChatSession::getStatus, "ACTIVE"));
    }

    /**
     * 列出用户的会话（按最后消息时间倒序，分页）
     */
    public List<ChatSession> listSessions(Long userId, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 1);
        return sessionMapper.selectList(new LambdaQueryWrapper<AiChatSession>()
                .eq(AiChatSession::getUserId, userId)
                .orderByDesc(AiChatSession::getLastMessageTime)
                .last("LIMIT " + safeSize + " OFFSET " + (safePage - 1) * safeSize))
                .stream().map(this::toChatSession).toList();
    }

    private ChatSession toChatSession(AiChatSession row) {
        ChatSession session = new ChatSession();
        session.setSessionId(row.getSessionId());
        session.setUserId(row.getUserId());
        session.setAgentCode(row.getAgentCode());
        session.setAgentVersion(row.getAgentVersion());
        session.setModelOverride(row.getModelOverride());
        session.setTitle(row.getTitle());
        session.setChannel(row.getChannel());
        session.setStatus(row.getStatus());
        session.setCreateTime(row.getCreateTime());
        session.setLastMessageTime(row.getLastMessageTime());
        return session;
    }

    private ChatMessage toChatMessage(AiChatMessage row) {
        ChatMessage message = new ChatMessage();
        message.setMessageId(row.getMessageId());
        message.setRole(row.getRole());
        message.setContent(row.getContent());
        message.setToolCalls(fromJson(row.getToolCalls()));
        message.setCitations(fromJson(row.getCitations()));
        message.setTraceId(row.getTraceId());
        message.setCreateTime(row.getCreateTime());
        return message;
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String s) {
            return s;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("消息字段序列化失败，丢弃: {}", e.getMessage());
            return null;
        }
    }

    private Object fromJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return json;
        }
    }

    @Data
    public static class ChatSession {
        private String sessionId;
        private Long userId;
        private String agentCode;
        private Integer agentVersion;
        private String modelOverride;
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
