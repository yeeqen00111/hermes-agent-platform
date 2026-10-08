package com.hermes.agent.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DataScopeParserTest {

    private final DataScopeParser parser = new DataScopeParser();

    @Test
    void parsesArrayAndScalarValues() {
        var scope = parser.parse("{\"environment\":[\"prod\",\"test\"],\"projectCode\":\"P1\"}");

        assertThat(scope).containsEntry("environment", java.util.List.of("prod", "test"));
        assertThat(scope).containsEntry("projectCode", java.util.List.of("P1"));
    }

    @Test
    void parsesWildcard() {
        var scope = parser.parse("{\"serviceName\":[\"*\"]}");

        assertThat(scope).containsEntry("serviceName", java.util.List.of("*"));
    }

    @Test
    void blankOrNullReturnsNull() {
        assertThat(parser.parse(null)).isNull();
        assertThat(parser.parse("   ")).isNull();
    }

    @Test
    void invalidJsonReturnsNull() {
        assertThat(parser.parse("{not json")).isNull();
    }

    @Test
    void nonObjectReturnsNull() {
        assertThat(parser.parse("[\"prod\"]")).isNull();
    }

    @Test
    void emptyObjectReturnsNull() {
        assertThat(parser.parse("{}")).isNull();
    }
}
