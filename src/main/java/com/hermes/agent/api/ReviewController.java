package com.hermes.agent.api;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hermes.agent.entity.CrCredential;
import com.hermes.agent.entity.CrRepository;
import com.hermes.agent.entity.CrReviewIssue;
import com.hermes.agent.entity.CrReviewParticipant;
import com.hermes.agent.entity.CrReviewRule;
import com.hermes.agent.entity.CrReviewTask;
import com.hermes.agent.mapper.CrRepositoryMapper;
import com.hermes.agent.mapper.CrReviewIssueMapper;
import com.hermes.agent.mapper.CrReviewRuleMapper;
import com.hermes.agent.review.CredentialService;
import com.hermes.agent.review.GitService;
import com.hermes.agent.review.ReviewParticipantService;
import com.hermes.agent.review.ReviewPermissionService;
import com.hermes.agent.review.ReviewReportService;
import com.hermes.agent.review.ReviewTaskService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 代码评审模块API：凭据/仓库/规则管理 + 任务触发 + Agent回传回调 + 复审闭环
 */
@RestController
@RequestMapping("/api/review")
@RequiredArgsConstructor
public class ReviewController {

    private final CredentialService credentialService;
    private final GitService gitService;
    private final ReviewTaskService reviewTaskService;
    private final ReviewReportService reportService;
    private final ReviewParticipantService participantService;
    private final ReviewPermissionService permissionService;
    private final CrRepositoryMapper repositoryMapper;
    private final CrReviewRuleMapper ruleMapper;
    private final CrReviewIssueMapper issueMapper;

    // ---------- 凭据管理 ----------

    @GetMapping("/credentials")
    public List<CrCredential> listCredentials() {
        return credentialService.list();
    }

    @PostMapping("/credentials")
    public CrCredential saveCredential(@RequestBody CrCredential credential) {
        return credentialService.save(credential);
    }

    // ---------- 仓库管理 ----------

    @GetMapping("/repos")
    public List<CrRepository> listRepos() {
        return repositoryMapper.selectList(new LambdaQueryWrapper<CrRepository>()
                .orderByDesc(CrRepository::getId));
    }

    @PostMapping("/repos")
    public CrRepository saveRepo(@RequestBody CrRepository repo) {
        if (repo.getId() != null) {
            repositoryMapper.updateById(repo);
        } else {
            repositoryMapper.insert(repo);
        }
        return repo;
    }

    @PostMapping("/repos/{id}/sync")
    public Map<String, Object> syncRepo(@PathVariable Long id) {
        CrRepository repo = repositoryMapper.selectById(id);
        String head = gitService.sync(repo);
        repositoryMapper.updateById(repo);
        return Map.of("success", true, "head", head);
    }

    // ---------- 评审规则 ----------

    @GetMapping("/rules")
    public List<CrReviewRule> listRules() {
        return ruleMapper.selectList(new LambdaQueryWrapper<CrReviewRule>()
                .orderByDesc(CrReviewRule::getId));
    }

    @PostMapping("/rules")
    public CrReviewRule saveRule(@RequestBody CrReviewRule rule) {
        if (rule.getId() != null) {
            ruleMapper.updateById(rule);
        } else {
            ruleMapper.insert(rule);
        }
        return rule;
    }

    // ---------- 评审任务 ----------

    @PostMapping("/tasks")
    public CrReviewTask trigger(@RequestBody TriggerRequest request,
                                @RequestHeader(value = "X-Actor-User-Id", required = false) Long userId) {
        permissionService.requireRole(request.getRuleId(), userId, "INITIATOR");
        CrReviewTask task = reviewTaskService.createTask(request.getRepoId(), request.getBranch(),
                request.getStartRevision(), "MANUAL",
                userId == null ? "anonymous" : String.valueOf(userId), request.getRuleId());
        reviewTaskService.dispatchAsync(task.getTaskUuid());
        return task;
    }

    @GetMapping("/tasks")
    public List<CrReviewTask> listTasks(@RequestParam(required = false) String status) {
        return reviewTaskService.list(status);
    }

    @GetMapping("/tasks/{uuid}")
    public Map<String, Object> getTask(@PathVariable String uuid) {
        CrReviewTask task = reportService.byUuid(uuid);
        if (task == null) {
            return Map.of("success", false, "message", "任务不存在");
        }
        return Map.of("success", true, "task", task,
                "reports", reportService.reports(uuid),
                "issues", reportService.issues(uuid));
    }

    @PostMapping("/tasks/{uuid}/re-review")
    public CrReviewTask reReview(@PathVariable String uuid,
                                 @RequestHeader(value = "X-Actor-User-Id", required = false) Long userId) {
        CrReviewTask task = reportService.byUuid(uuid);
        permissionService.requireTaskRole(task, userId, "INITIATOR");
        return reviewTaskService.startReReview(uuid, userId == null ? "anonymous" : String.valueOf(userId));
    }

    @PostMapping("/tasks/{uuid}/close")
    public CrReviewTask close(@PathVariable String uuid,
                              @RequestHeader(value = "X-Actor-User-Id", required = false) Long userId) {
        CrReviewTask task = reportService.byUuid(uuid);
        permissionService.requireTaskRole(task, userId, "CLOSER");
        return reviewTaskService.close(uuid);
    }

    // ---------- Agent 回传回调（报告/失败） ----------

    @PostMapping("/tasks/{uuid}/report")
    public Map<String, Object> uploadReport(@PathVariable String uuid, @RequestBody ReportCallback request) {
        var report = reportService.upload(uuid, request.getMarkdown(), request.getScores(),
                request.getIssues(), request.getModelName());
        return Map.of("success", true, "reportId", report.getId());
    }

    @PostMapping("/tasks/{uuid}/failure")
    public Map<String, Object> failureCallback(@PathVariable String uuid, @RequestBody FailureCallback request) {
        reportService.failureCallback(uuid, request.getErrorMessage());
        return Map.of("success", true);
    }

    // ---------- WEBHOOK ----------

    @PostMapping("/webhook")
    public Map<String, Object> webhook(@RequestBody WebhookPayload payload) {
        List<CrReviewTask> tasks = reviewTaskService.onWebhook(
                payload.getRepoCode(), payload.getBranch(), payload.getRevision());
        return Map.of("success", true, "tasks", tasks);
    }

    // ---------- 评审问题闭环 ----------

    @PostMapping("/issues/{id}/status")
    public Map<String, Object> updateIssueStatus(@PathVariable Long id, @RequestBody IssueStatusRequest request,
                                                 @RequestHeader(value = "X-Actor-User-Id", required = false) Long userId) {
        CrReviewIssue existing = issueMapper.selectById(id);
        if (existing == null) {
            return Map.of("success", false, "message", "问题不存在");
        }
        CrReviewTask task = reportService.byUuid(existing.getTaskUuid());
        permissionService.requireTaskRole(task, userId, "CLOSER");
        CrReviewIssue issue = new CrReviewIssue();
        issue.setId(id);
        issue.setStatus(request.getStatus());
        issue.setRecheckNote(request.getNote());
        reportService.updateIssue(issue);
        return Map.of("success", true);
    }

    /**
     * 「查看自己的问题记录」：用户参与过的评审规则下所有任务的问题
     */
    @GetMapping("/issues")
    public List<CrReviewIssue> issuesByUser(@RequestParam Long userId,
                                            @RequestParam(required = false) String status) {
        return reportService.issuesByUser(userId, status);
    }

    // ---------- 规则参与人（三角色 + 邮箱/飞书） ----------

    @GetMapping("/rules/{ruleId}/participants")
    public List<CrReviewParticipant> listParticipants(@PathVariable Long ruleId) {
        return participantService.list(ruleId);
    }

    @PostMapping("/rules/{ruleId}/participants")
    public CrReviewParticipant saveParticipant(@PathVariable Long ruleId,
                                               @RequestBody CrReviewParticipant participant) {
        participant.setRuleId(ruleId);
        return participantService.save(participant);
    }

    @DeleteMapping("/participants/{id}")
    public Map<String, Object> deleteParticipant(@PathVariable Long id) {
        participantService.delete(id);
        return Map.of("success", true);
    }

    @Data
    public static class TriggerRequest {
        private Long repoId;
        private String branch;
        private String startRevision;
        private Long ruleId;
    }

    @Data
    public static class ReportCallback {
        private String markdown;
        private String scores;
        private String issues;
        private String modelName;
    }

    @Data
    public static class FailureCallback {
        private String errorMessage;
    }

    @Data
    public static class WebhookPayload {
        private String repoCode;
        private String branch;
        private String revision;
    }

    @Data
    public static class IssueStatusRequest {
        private String status;
        private String note;
    }
}
