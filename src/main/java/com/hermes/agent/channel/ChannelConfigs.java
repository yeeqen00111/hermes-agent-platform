package com.hermes.agent.channel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 渠道扩展配置（{@code ai_channel.config} JSON）读取工具。
 */
final class ChannelConfigs {

    private ChannelConfigs() {
    }

    static String value(ObjectMapper mapper, String configJson, String key) {
        if (configJson == null || configJson.isBlank()) {
            return null;
        }
        try {
            JsonNode node = mapper.readTree(configJson);
            String value = node.path(key).asText(null);
            return (value == null || value.isBlank()) ? null : value;
        } catch (Exception e) {
            return null;
        }
    }
}
