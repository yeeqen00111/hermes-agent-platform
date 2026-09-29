package com.hermes.agent.dto;

import lombok.Data;
import java.util.List;

/**
 * 工具调用返回信封（对齐原方案 §11.4）
 */
@Data
public class ToolResponse {
    /**
     * 是否成功
     */
    private Boolean success;

    /**
     * 返回数据
     */
    private Object data;

    /**
     * 证据引用列表
     */
    private List<Citation> citations;

    /**
     * 错误码
     */
    private String errorCode;

    /**
     * 错误消息
     */
    private String errorMessage;

    /**
     * 耗时(ms)
     */
    private Long durationMs;

    @Data
    public static class Citation {
        /**
         * 引用类型：log/alert/nacos/knowledge/metric/report/mcp
         */
        private String kind;

        /**
         * 来源（索引名/表名等）
         */
        private String source;

        /**
         * 标题
         */
        private String title;

        /**
         * 定位符（eventId/alertId/chunkId等）
         */
        private String locator;

        /**
         * 可选深链
         */
        private String uri;

        /**
         * 可选摘录
         */
        private String snippet;
    }
}
