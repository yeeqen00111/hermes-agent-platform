package com.hermes.agent.review;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * GitLab Webhook 解析与校验（白板·代码评审「怎么触发？WEBHOOK」）。
 *
 * <p>支持 Push Hook 与 Merge Request Hook，解析出 (repoCode, branch, revision)，
 * 交给 {@link ReviewTaskService#onWebhook} 按规则建任务；密钥校验 {@code X-Gitlab-Token}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GitLabWebhookParser {

    /** MR 触发评审的动作 */
    private static final List<String> MR_TRIGGER_ACTIONS = List.of("open", "update", "reopen");

    private final ObjectMapper objectMapper;

    /**
     * 解析结果；{@code accepted()} 为 false 时 {@code reason} 说明忽略原因。
     */
    public record Parsed(String kind, String repoCode, String branch, String revision, String reason) {

        public boolean accepted() {
            return repoCode != null && !repoCode.isBlank()
                    && branch != null && !branch.isBlank()
                    && revision != null && !revision.isBlank();
        }
    }

    public Parsed parse(String body) {
        if (body == null || body.isBlank()) {
            return new Parsed("unknown", null, null, null, "EMPTY_BODY");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (Exception e) {
            log.warn("Webhook 载荷不是合法 JSON: {}", e.getMessage());
            return new Parsed("unknown", null, null, null, "BAD_PAYLOAD");
        }
        String kind = root.path("object_kind").asText("");
        return switch (kind) {
            case "push" -> parsePush(root);
            case "merge_request" -> parseMergeRequest(root);
            case "tag_push" -> new Parsed("tag_push", null, null, null, "TAG_PUSH_IGNORED");
            default -> new Parsed(kind.isBlank() ? "unknown" : kind, null, null, null, "UNSUPPORTED_EVENT");
        };
    }

    private Parsed parsePush(JsonNode root) {
        String ref = root.path("ref").asText("");
        String branch = ref.startsWith("refs/heads/") ? ref.substring("refs/heads/".length()) : ref;
        String after = root.path("after").asText("");
        if (after.isBlank() || after.matches("0+")) {
            return new Parsed("push", null, branch, null, "BRANCH_DELETED");
        }
        String revision = root.path("checkout_sha").asText("");
        if (revision.isBlank()) {
            revision = after;
        }
        return new Parsed("push", repoCode(root), branch, revision, null);
    }

    private Parsed parseMergeRequest(JsonNode root) {
        JsonNode attrs = root.path("object_attributes");
        String action = attrs.path("action").asText("");
        if (!MR_TRIGGER_ACTIONS.contains(action)) {
            return new Parsed("merge_request", null, attrs.path("source_branch").asText(null), null, "ACTION_SKIPPED:" + action);
        }
        String branch = attrs.path("source_branch").asText("");
        String revision = attrs.path("last_commit").path("id").asText("");
        return new Parsed("merge_request", repoCode(root), branch, revision, null);
    }

    /**
     * 仓库编码：优先 project.path，其次 repository.name，最后 path_with_namespace 的末段。
     */
    private String repoCode(JsonNode root) {
        String path = root.path("project").path("path").asText("");
        if (path.isBlank()) {
            path = root.path("repository").path("name").asText("");
        }
        if (path.isBlank()) {
            String ns = root.path("project").path("path_with_namespace").asText("");
            path = ns.contains("/") ? ns.substring(ns.lastIndexOf('/') + 1) : ns;
        }
        return path.isBlank() ? null : path;
    }

    /**
     * 校验 X-Gitlab-Token：未配置密钥时放行（开发联调，调用方负责告警），配置后必须精确匹配。
     */
    public static boolean tokenValid(String provided, String configured) {
        if (configured == null || configured.isBlank()) {
            return true;
        }
        return configured.equals(provided);
    }
}
