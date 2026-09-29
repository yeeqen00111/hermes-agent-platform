package com.hermes.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SSE事件
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SSEEvent {
    /**
     * 事件类型
     */
    private String event;

    /**
     * 事件数据
     */
    private Object data;

    /**
     * 消息ID（可选）
     */
    private String id;

    public static SSEEvent of(String event, Object data) {
        return new SSEEvent(event, data, null);
    }

    public static SSEEvent of(String event, Object data, String id) {
        return new SSEEvent(event, data, id);
    }
}
