package com.hermes.agent.tool;

import com.hermes.agent.dto.ToolRequest;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GuardrailTest {

    private final Guardrail guardrail = new Guardrail();

    private ToolDefinition def(Map<String, Object> schema, List<String> scopeFields) {
        ToolDefinition d = new ToolDefinition();
        d.setToolCode("t");
        d.setParamSchema(schema);
        d.setScopeFields(scopeFields);
        return d;
    }

    private Map<String, Object> schema(List<String> required, Map<String, Object> props) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("type", "object");
        s.put("properties", props);
        s.put("required", required);
        return s;
    }

    private ToolRequest req(Map<String, Object> args) {
        ToolRequest r = new ToolRequest();
        r.setArguments(args);
        return r;
    }

    private Map<String, Object> args(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    @Test
    void missingRequiredArgumentIsRejected() {
        ToolDefinition d = def(schema(List.of("service", "from", "to"),
                Map.of("service", Map.of("type", "string"))), List.of());

        var result = guardrail.validate(d, req(args("service", "order-svc")));

        assertThat(result.valid()).isFalse();
        assertThat(result.errorCode()).isEqualTo("INVALID_ARGUMENT");
    }

    @Test
    void blankRequiredArgumentIsRejected() {
        ToolDefinition d = def(schema(List.of("service", "from", "to"), Map.of()), List.of());

        var result = guardrail.validate(d, req(args("service", "   ", "from", "a", "to", "b")));

        assertThat(result.valid()).isFalse();
        assertThat(result.errorCode()).isEqualTo("INVALID_ARGUMENT");
    }

    @Test
    void allRequiredProvidedPasses() {
        ToolDefinition d = def(schema(List.of("service", "from", "to"), Map.of()), List.of());

        var result = guardrail.validate(d, req(args("service", "order-svc", "from", "a", "to", "b")));

        assertThat(result.valid()).isTrue();
    }

    @Test
    void nonNumericIntegerArgumentIsRejected() {
        ToolDefinition d = def(schema(List.of(), Map.of("limit", Map.of("type", "integer"))), List.of());

        var result = guardrail.validate(d, req(args("limit", "abc")));

        assertThat(result.valid()).isFalse();
        assertThat(result.errorCode()).isEqualTo("INVALID_ARGUMENT");
    }

    @Test
    void numericStringIntegerArgumentPasses() {
        ToolDefinition d = def(schema(List.of(), Map.of("limit", Map.of("type", "integer"))), List.of());

        var result = guardrail.validate(d, req(args("limit", "50")));

        assertThat(result.valid()).isTrue();
    }

    @Test
    void absentSchemaAllowsAnything() {
        ToolDefinition d = def(null, List.of());

        var result = guardrail.validate(d, req(Map.of()));

        assertThat(result.valid()).isTrue();
    }

    @Test
    void timeoutOver60sIsRejected() {
        ToolDefinition d = def(null, List.of());
        ToolRequest request = req(Map.of());
        request.setTimeoutMs(60_001);

        var result = guardrail.validate(d, request);

        assertThat(result.valid()).isFalse();
        assertThat(result.errorCode()).isEqualTo("INVALID_ARGUMENT");
    }

    @Test
    void scopeFieldsPresentCurrentlyPassThrough() {
        ToolDefinition d = def(schema(List.of(), Map.of()), List.of("environment"));

        var result = guardrail.validate(d, req(args("environment", "prod")));

        assertThat(result.valid()).isTrue();
    }
}
