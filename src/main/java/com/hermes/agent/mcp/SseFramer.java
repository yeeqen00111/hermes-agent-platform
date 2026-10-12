package com.hermes.agent.mcp;

import java.util.ArrayList;
import java.util.List;

/**
 * SSE（Server-Sent Events）帧解析器：把逐行读入的流切分成「一帧 = 一个事件」。
 *
 * <p>MCP streamable HTTP 的传输形态：服务端以 {@code text/event-stream} 推送
 * {@code event: message} + {@code data: <json-rpc>}，空行分帧。这里按 W3C SSE 规范
 * 逐行喂入，凑齐一帧即返回，不要求连接关闭——长连接的关键就在这。
 */
public final class SseFramer {

    /** SSE 一帧：event 字段（默认 message）+ data 行拼成的正文 */
    public record Frame(String event, String data) {
    }

    private String event;
    private final StringBuilder data = new StringBuilder();
    private boolean hasFields;

    /**
     * 喂入一行（不含换行符）。凑满一帧返回该帧，否则返回 {@code null}。
     */
    public synchronized Frame feed(String line) {
        if (line == null) {
            return null;
        }
        if (line.isEmpty()) {
            return dispatch();
        }
        if (line.startsWith(":")) {
            // 注释行（心跳/keep-alive 常见），忽略
            return null;
        }
        int colon = line.indexOf(':');
        String field;
        String value;
        if (colon < 0) {
            field = line;
            value = "";
        } else {
            field = line.substring(0, colon);
            value = line.substring(colon + 1);
            if (value.startsWith(" ")) {
                value = value.substring(1);
            }
        }
        switch (field) {
            case "event" -> {
                event = value;
                hasFields = true;
            }
            case "data" -> {
                data.append(value).append('\n');
                hasFields = true;
            }
            // id / retry / 其他字段对 JSON-RPC 路由无用，忽略
            default -> {
            }
        }
        return null;
    }

    /** 空行到达：如果收到过任何字段就派发一帧 */
    private Frame dispatch() {
        if (!hasFields) {
            reset();
            return null;
        }
        Frame frame = new Frame(event == null || event.isBlank() ? "message" : event,
                stripTrailingNewline(data.toString()));
        reset();
        return frame;
    }

    private void reset() {
        event = null;
        data.setLength(0);
        hasFields = false;
    }

    private static String stripTrailingNewline(String s) {
        return s.endsWith("\n") ? s.substring(0, s.length() - 1) : s;
    }

    /**
     * 一次性解析整段 SSE 文本（供测试与非流式场景使用）。
     */
    public static List<Frame> parse(String text) {
        List<Frame> frames = new ArrayList<>();
        SseFramer framer = new SseFramer();
        for (String line : text.split("\r\n|\r|\n", -1)) {
            Frame frame = framer.feed(line);
            if (frame != null) {
                frames.add(frame);
            }
        }
        return frames;
    }
}
