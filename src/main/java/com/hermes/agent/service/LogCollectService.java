package com.hermes.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hermes.agent.entity.LogChannel;
import com.hermes.agent.entity.LogParseRule;
import com.hermes.agent.mapper.LogChannelMapper;
import com.hermes.agent.mapper.LogParseRuleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 日志采集（白板·系统管理）：
 * - 采集通道：配置从哪采集（kafka / 消息队列 / 兼容 filebeat.log），并给出 filebeat 配置示例；
 * - 解析规则：系统/时间/级别/内容/服务 五级 JSON 解析，支持按示例日志预览。
 */
@Service
@RequiredArgsConstructor
public class LogCollectService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final LogChannelMapper channelMapper;
    private final LogParseRuleMapper parseRuleMapper;

    // ---------- 采集通道 ----------

    public List<LogChannel> listChannels() {
        return channelMapper.selectList(new LambdaQueryWrapper<LogChannel>()
                .orderByAsc(LogChannel::getId));
    }

    public LogChannel saveChannel(LogChannel channel) {
        if (channel.getId() != null) {
            channelMapper.updateById(channel);
        } else {
            channelMapper.insert(channel);
        }
        return channel;
    }

    public void deleteChannel(Long id) {
        channelMapper.deleteById(id);
    }

    /**
     * 依据通道配置生成 filebeat 配置示例（白板：给出 filebeat 配置示例）。
     */
    public Map<String, Object> filebeatExample(String channelCode) {
        LogChannel channel = channelMapper.selectOne(new LambdaQueryWrapper<LogChannel>()
                .eq(LogChannel::getCode, channelCode));
        if (channel == null) {
            return Map.of("success", false, "message", "通道不存在: " + channelCode);
        }
        Map<String, Object> cfg = readObject(channel.getConfig());
        StringBuilder yaml = new StringBuilder();
        yaml.append("# filebeat.yml —— 通道 ").append(channel.getCode()).append(" 采集示例\n");
        yaml.append("filebeat.inputs:\n");
        yaml.append("  - type: filestream\n");
        yaml.append("    id: ").append(channel.getCode()).append("\n");
        yaml.append("    paths:\n");
        yaml.append("      - ").append(str(cfg.get("path"), "/var/log/" + channel.getCode() + "/*.log")).append("\n");
        yaml.append("    parsers:\n");
        yaml.append("      - ndjson:\n");
        yaml.append("          target: \"\"\n");
        yaml.append("          overwrite_keys: true\n");
        yaml.append("output.kafka:\n");
        yaml.append("  hosts: [\"").append(str(cfg.get("brokers"), "localhost:9092")).append("\"]\n");
        yaml.append("  topic: \"").append(str(cfg.get("topic"), channel.getCode())).append("\"\n");
        Object group = cfg.get("groupId");
        if (group != null) {
            yaml.append("  group_id: \"").append(group).append("\"\n");
        }
        return Map.of("success", true, "channelType", String.valueOf(channel.getChannelType()),
                "content", yaml.toString());
    }

    // ---------- 解析规则 ----------

    public List<LogParseRule> listParseRules() {
        return parseRuleMapper.selectList(new LambdaQueryWrapper<LogParseRule>()
                .orderByAsc(LogParseRule::getId));
    }

    public LogParseRule saveParseRule(LogParseRule rule) {
        if (rule.getId() != null) {
            parseRuleMapper.updateById(rule);
        } else {
            parseRuleMapper.insert(rule);
        }
        return rule;
    }

    public void deleteParseRule(Long id) {
        parseRuleMapper.deleteById(id);
    }

    /**
     * 用解析规则把示例 JSON 映射为规范字段，供前端预览（成功/失败都返回 message）。
     */
    public Map<String, Object> preview(LogParseRule rule) {
        String sample = rule == null || rule.getSampleJson() == null ? null : rule.getSampleJson().trim();
        if (sample == null || sample.isEmpty()) {
            return Map.of("success", false, "message", "示例日志 JSON 为空");
        }
        JsonNode node;
        try {
            node = MAPPER.readTree(sample);
        } catch (Exception e) {
            return Map.of("success", false, "message", "示例日志不是合法 JSON: " + e.getMessage());
        }
        if (node == null || !node.isObject()) {
            return Map.of("success", false, "message", "示例日志必须是 JSON 对象");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        out.put("system", text(node, rule.getSystemField()));
        out.put("time", text(node, rule.getTimeField()));
        out.put("level", mapLevel(text(node, rule.getLevelField()), rule.getLevelMapping()));
        out.put("content", text(node, rule.getContentField()));
        out.put("service", text(node, rule.getServiceField()));
        out.put("raw", node);
        return out;
    }

    private String mapLevel(String level, String levelMapping) {
        if (level == null || levelMapping == null || levelMapping.isBlank()) {
            return level;
        }
        Object mapped = readObject(levelMapping).get(level);
        return mapped == null ? level : String.valueOf(mapped);
    }

    private String text(JsonNode node, String field) {
        if (field == null || field.isBlank()) {
            return null;
        }
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asText();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readObject(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return MAPPER.readValue(json, Map.class);
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private String str(Object v, String fallback) {
        return v == null || String.valueOf(v).isBlank() ? fallback : String.valueOf(v);
    }
}
