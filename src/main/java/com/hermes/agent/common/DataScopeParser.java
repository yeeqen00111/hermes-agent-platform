package com.hermes.agent.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * X-Data-Scope 解析（interface-contract §3.6）。
 *
 * <p>入参形如 {@code {"environment":["prod","test"],"projectCode":"P1","serviceName":["*"]}}，
 * 标量与数组皆可，{@code "*"} 表示通配；输出 {@code Map<维度, 允许值列表>}。
 * 空白或非法输入返回 {@code null}，表示**未注入**——是否放行由 {@link com.hermes.agent.tool.Guardrail} 按 fail-open 策略决定。
 */
@Slf4j
@Component
public class DataScopeParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public Map<String, List<String>> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            JsonNode root = MAPPER.readTree(raw);
            if (!root.isObject()) {
                log.warn("X-Data-Scope 非 JSON 对象，忽略: {}", raw);
                return null;
            }
            Map<String, List<String>> scope = new LinkedHashMap<>();
            root.fields().forEachRemaining(entry -> {
                List<String> values = new ArrayList<>();
                JsonNode value = entry.getValue();
                if (value != null && value.isArray()) {
                    value.forEach(node -> {
                        if (!node.isNull() && !node.asText().isBlank()) {
                            values.add(node.asText());
                        }
                    });
                } else if (value != null && !value.isNull() && !value.asText().isBlank()) {
                    values.add(value.asText());
                }
                if (!values.isEmpty()) {
                    scope.put(entry.getKey(), values);
                }
            });
            return scope.isEmpty() ? null : scope;
        } catch (Exception e) {
            log.warn("X-Data-Scope 解析失败，忽略: {}", e.getMessage());
            return null;
        }
    }
}
