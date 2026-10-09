package com.hermes.agent.review;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GitLab Webhook 解析与密钥校验（模拟真实载荷，离线）。
 */
class GitLabWebhookParserTest {

    private final GitLabWebhookParser parser = new GitLabWebhookParser(new ObjectMapper());

    @Test
    void pushAccepted() {
        String body = """
                {"object_kind":"push","ref":"refs/heads/release/1.0",
                 "checkout_sha":"abc123","after":"abc123",
                 "project":{"path":"hermes-agent-platform","path_with_namespace":"group/hermes-agent-platform"},
                 "repository":{"name":"hermes-agent-platform"}}
                """;
        GitLabWebhookParser.Parsed p = parser.parse(body);
        assertThat(p.accepted()).isTrue();
        assertThat(p.kind()).isEqualTo("push");
        assertThat(p.repoCode()).isEqualTo("hermes-agent-platform");
        assertThat(p.branch()).isEqualTo("release/1.0");
        assertThat(p.revision()).isEqualTo("abc123");
    }

    @Test
    void pushFallsBackToAfterWhenNoCheckoutSha() {
        String body = """
                {"object_kind":"push","ref":"refs/heads/main","after":"deadbeef",
                 "project":{"path":"order-platform"}}
                """;
        GitLabWebhookParser.Parsed p = parser.parse(body);
        assertThat(p.accepted()).isTrue();
        assertThat(p.branch()).isEqualTo("main");
        assertThat(p.revision()).isEqualTo("deadbeef");
    }

    @Test
    void pushBranchDeletionIgnored() {
        String body = """
                {"object_kind":"push","ref":"refs/heads/feature-x","after":"0000000000000000000000000000000000000000",
                 "project":{"path":"order-platform"}}
                """;
        GitLabWebhookParser.Parsed p = parser.parse(body);
        assertThat(p.accepted()).isFalse();
        assertThat(p.reason()).isEqualTo("BRANCH_DELETED");
    }

    @Test
    void mergeRequestOpenAccepted() {
        String body = """
                {"object_kind":"merge_request",
                 "project":{"path":"order-platform"},
                 "object_attributes":{"action":"open","source_branch":"feature/pay","last_commit":{"id":"c0ffee"}}}
                """;
        GitLabWebhookParser.Parsed p = parser.parse(body);
        assertThat(p.accepted()).isTrue();
        assertThat(p.kind()).isEqualTo("merge_request");
        assertThat(p.repoCode()).isEqualTo("order-platform");
        assertThat(p.branch()).isEqualTo("feature/pay");
        assertThat(p.revision()).isEqualTo("c0ffee");
    }

    @Test
    void mergeRequestClosedSkipped() {
        String body = """
                {"object_kind":"merge_request","project":{"path":"order-platform"},
                 "object_attributes":{"action":"close","source_branch":"feature/pay"}}
                """;
        GitLabWebhookParser.Parsed p = parser.parse(body);
        assertThat(p.accepted()).isFalse();
        assertThat(p.reason()).isEqualTo("ACTION_SKIPPED:close");
    }

    @Test
    void tagPushIgnored() {
        assertThat(parser.parse("{\"object_kind\":\"tag_push\"}").reason()).isEqualTo("TAG_PUSH_IGNORED");
    }

    @Test
    void unknownEventUnsupported() {
        GitLabWebhookParser.Parsed p = parser.parse("{\"object_kind\":\"pipeline\"}");
        assertThat(p.accepted()).isFalse();
        assertThat(p.reason()).isEqualTo("UNSUPPORTED_EVENT");
    }

    @Test
    void badJsonReported() {
        assertThat(parser.parse("not-json").reason()).isEqualTo("BAD_PAYLOAD");
        assertThat(parser.parse("").reason()).isEqualTo("EMPTY_BODY");
    }

    @Test
    void repoCodeFromPathWithNamespaceWhenPathMissing() {
        String body = """
                {"object_kind":"push","ref":"refs/heads/main","after":"a1",
                 "project":{"path_with_namespace":"group/sub/order-platform"}}
                """;
        assertThat(parser.parse(body).repoCode()).isEqualTo("order-platform");
    }

    @Test
    void tokenValidation() {
        assertThat(GitLabWebhookParser.tokenValid(null, "")).isTrue();
        assertThat(GitLabWebhookParser.tokenValid("anything", null)).isTrue();
        assertThat(GitLabWebhookParser.tokenValid("right", "right")).isTrue();
        assertThat(GitLabWebhookParser.tokenValid("wrong", "right")).isFalse();
        assertThat(GitLabWebhookParser.tokenValid(null, "right")).isFalse();
    }
}
