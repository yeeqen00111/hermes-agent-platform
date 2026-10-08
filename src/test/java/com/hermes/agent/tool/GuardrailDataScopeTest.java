package com.hermes.agent.tool;

import com.hermes.agent.dto.ToolRequest;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 数据范围校验（interface-contract §3.6 / ADR-013）：
 * X-Data-Scope 注入后按维度比对；未注入则 fail-open 放行并告警。
 */
class GuardrailDataScopeTest {

    private final Guardrail guardrail = new Guardrail();

    private ToolDefinition def(List<String> scopeFields) {
        ToolDefinition d = new ToolDefinition();
        d.setToolCode("t");
        d.setScopeFields(scopeFields);
        return d;
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

    private Map<String, List<String>> scope(Object... kv) {
        Map<String, List<String>> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            Object v = kv[i + 1];
            m.put(String.valueOf(kv[i]), v instanceof List<?> l
                    ? l.stream().map(String::valueOf).toList()
                    : List.of(String.valueOf(v)));
        }
        return m;
    }

    @Test
    void noScopeInjectedFailsOpen() {
        var result = guardrail.validate(def(List.of("environment")), req(args("environment", "prod")));

        assertThat(result.valid()).isTrue();
    }

    @Test
    void nonDataDimensionIsSkipped() {
        ToolRequest request = req(args("skillCode", "code-review-basic"));
        request.setDataScope(scope("environment", List.of("prod")));

        var result = guardrail.validate(def(List.of("skillCode")), request);

        assertThat(result.valid()).isTrue();
    }

    @Test
    void environmentWithinScopePasses() {
        ToolRequest request = req(args("environment", "test"));
        request.setDataScope(scope("environment", List.of("prod", "test")));

        assertThat(guardrail.validate(def(List.of("environment")), request).valid()).isTrue();
    }

    @Test
    void environmentOutsideScopeDenied() {
        ToolRequest request = req(args("environment", "prod"));
        request.setDataScope(scope("environment", List.of("test")));

        var result = guardrail.validate(def(List.of("environment")), request);

        assertThat(result.valid()).isFalse();
        assertThat(result.errorCode()).isEqualTo("SCOPE_DENIED");
    }

    @Test
    void wildcardScopeAllowsAnything() {
        ToolRequest request = req(args("environment", "prod"));
        request.setDataScope(scope("environment", List.of("*")));

        assertThat(guardrail.validate(def(List.of("environment")), request).valid()).isTrue();
    }

    @Test
    void projectCodeOutsideScopeDenied() {
        ToolRequest request = req(args("projectCode", "P2"));
        request.setDataScope(scope("projectCode", List.of("P1")));

        var result = guardrail.validate(def(List.of("projectCode")), request);

        assertThat(result.valid()).isFalse();
        assertThat(result.errorCode()).isEqualTo("SCOPE_DENIED");
    }

    @Test
    void serviceNameScopeFieldMapsToServiceArgument() {
        ToolRequest ok = req(args("service", "order-svc"));
        ok.setDataScope(scope("serviceName", List.of("order-svc")));
        assertThat(guardrail.validate(def(List.of("serviceName")), ok).valid()).isTrue();

        ToolRequest denied = req(args("service", "payment-svc"));
        denied.setDataScope(scope("serviceName", List.of("order-svc")));
        assertThat(guardrail.validate(def(List.of("serviceName")), denied).errorCode()).isEqualTo("SCOPE_DENIED");
    }

    @Test
    void serverInjectedEnvironmentWinsOverModelArguments() {
        ToolRequest request = req(args("environment", "test"));
        request.setEnvironment("prod");
        request.setDataScope(scope("environment", List.of("test")));

        var result = guardrail.validate(def(List.of("environment")), request);

        assertThat(result.valid()).isFalse();
        assertThat(result.errorCode()).isEqualTo("SCOPE_DENIED");
    }

    @Test
    void dimensionNotPresentInScopeIsUnrestricted() {
        ToolRequest request = req(args("system", "crm"));
        request.setDataScope(scope("environment", List.of("prod")));

        assertThat(guardrail.validate(def(List.of("system")), request).valid()).isTrue();
    }

    @Test
    void environmentMatchIsCaseInsensitive() {
        ToolRequest request = req(args());
        request.setEnvironment("prod");
        request.setDataScope(scope("environment", List.of("PROD")));

        assertThat(guardrail.validate(def(List.of("environment")), request).valid()).isTrue();
    }

    @Test
    void unmatchedScopeFieldWithoutArgumentIsAllowed() {
        ToolRequest request = req(args());
        request.setDataScope(scope("environment", List.of("prod")));

        assertThat(guardrail.validate(def(List.of("environment")), request).valid()).isTrue();
    }
}
