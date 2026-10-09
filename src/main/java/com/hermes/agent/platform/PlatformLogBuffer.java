package com.hermes.agent.platform;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 平台日志缓冲（白板·平台层·平台配置）：挂一个环形 Appender 到 root logger，
 * 提供近期运行日志（级别/关键字过滤）给「平台日志」界面，避免引入外部日志服务。
 */
@Component
public class PlatformLogBuffer extends AppenderBase<ILoggingEvent> {

    /** 环形缓冲容量 */
    private static final int MAX = 500;

    private final Deque<Map<String, Object>> buffer = new ConcurrentLinkedDeque<>();

    @PostConstruct
    void attach() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        setContext(context);
        start();
        context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).addAppender(this);
    }

    @PreDestroy
    void detach() {
        try {
            LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
            context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).detachAppender(this);
            stop();
        } catch (Exception ignored) {
            // 关闭阶段忽略
        }
    }

    @Override
    protected void append(ILoggingEvent event) {
        record(event.getLevel().toString(), event.getLoggerName(), event.getThreadName(),
                event.getFormattedMessage(), event.getTimeStamp());
    }

    /** 记录一条日志（append 与测试共用） */
    public void record(String level, String logger, String thread, String message, long timestampMs) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("time", LocalDateTime.ofInstant(Instant.ofEpochMilli(timestampMs), ZoneId.systemDefault()));
        row.put("level", level);
        row.put("logger", logger);
        row.put("thread", thread);
        row.put("message", message);
        buffer.addFirst(row);
        while (buffer.size() > MAX) {
            buffer.pollLast();
        }
    }

    /** 查询近期日志（级别精确匹配 / 关键字包含 / 时间倒序） */
    public List<Map<String, Object>> query(String level, String keyword, int limit) {
        int max = Math.max(1, Math.min(limit, MAX));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : buffer) {
            if (level != null && !level.isBlank() && !level.equalsIgnoreCase(String.valueOf(row.get("level")))) {
                continue;
            }
            if (keyword != null && !keyword.isBlank()
                    && !String.valueOf(row.get("message")).toLowerCase().contains(keyword.toLowerCase())) {
                continue;
            }
            out.add(row);
            if (out.size() >= max) {
                break;
            }
        }
        return out;
    }

    public int size() {
        return buffer.size();
    }
}
