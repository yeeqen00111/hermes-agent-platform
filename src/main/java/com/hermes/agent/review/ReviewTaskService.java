package com.hermes.agent.review;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hermes.agent.entity.CrRepository;
import com.hermes.agent.entity.CrReviewRule;
import com.hermes.agent.entity.CrReviewTask;
import com.hermes.agent.mapper.CrRepositoryMapper;
import com.hermes.agent.mapper.CrReviewRuleMapper;
import com.hermes.agent.mapper.CrReviewTaskMapper;
import com.hermes.agent.runtime.AgentRunRequest;
import com.hermes.agent.runtime.AgentRunResult;
import com.hermes.agent.runtime.AgentRuntime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 评审任务编排：触发（人工/WEBHOOK/定期）→ 平台准备（仓库同步+提交区间+上次报告）
 * → 调用单一基座 code-reviewer 身份 → 报告回传/失败回调推进状态机
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewTaskService {

    private static final String REVIEWER_AGENT = "code-reviewer";

    private final CrReviewTaskMapper taskMapper;
    private final CrRepositoryMapper repositoryMapper;
    private final CrReviewRuleMapper ruleMapper;
    private final GitService gitService;
    private final ReviewReportService reportService;
    private final AgentRuntime agentRuntime;

    public CrReviewTask createTask(Long repoId, String branch, String startRevision,
                                   String triggerType, String triggerBy, Long ruleId) {
        CrRepository repo = repositoryMapper.selectById(repoId);
        if (repo == null) {
            throw new IllegalArgumentException("仓库不存在: " + repoId);
        }
        CrReviewTask task = new CrReviewTask();
        task.setTaskUuid(UUID.randomUUID().toString());
        task.setRepoId(repoId);
        task.setBranch(branch == null || branch.isBlank() ? repo.getDefaultBranch() : branch);
        task.setStartRevision(startRevision);
        task.setStatus("PENDING");
        task.setTriggerType(triggerType);
        task.setTriggerBy(triggerBy);
        task.setRuleId(ruleId);
        taskMapper.insert(task);
        return task;
    }

    /**
     * 派发：异步执行，失败/超时也走回调
     */
    public void dispatchAsync(String taskUuid) {
        CompletableFuture.runAsync(() -> dispatch(taskUuid));
    }

    public void dispatch(String taskUuid) {
        CrReviewTask task = reportService.byUuid(taskUuid);
        if (task == null) {
            return;
        }
        CrRepository repo = repositoryMapper.selectById(task.getRepoId());
        task.setStatus("REVIEWING".equals(task.getStatus()) || "RE_REVIEWING".equals(task.getStatus())
                ? task.getStatus() : "REVIEWING");
        task.setDispatchTime(LocalDateTime.now());
        taskMapper.updateById(task);
        try {
            String head = gitService.sync(repo);
            task.setEndRevision(head);
            String commitInfo = gitService.commitRangeInfo(repo, task.getBranch(), task.getStartRevision());
            String lastReport = lastReportExcerpt(task);

            String extra = buildExtraContext(repo, task, commitInfo, lastReport);
            AgentRunResult result = agentRuntime.run(AgentRunRequest.builder()
                    .agentCode(REVIEWER_AGENT)
                    .userId(0L)
                    .bizKey(taskUuid)
                    .traceId("review-" + taskUuid)
                    .channel("review")
                    .userInput("请对仓库 " + repo.getName() + " 分支 " + task.getBranch()
                            + " 区间 " + task.getStartRevision() + ".." + head + " 的提交进行代码评审，"
                            + "输出markdown评审报告，并在结尾附```json {\"scores\":{...},\"issues\":[...]} ```结构化块。")
                    .extraContext(extra)
                    .build());
            // Agent侧报告上传回调（内部同路径）
            reportService.upload(taskUuid, result.getReply(), null, null,
                    "code-reviewer/" + result.getRunId());
        } catch (Exception e) {
            reportService.failureCallback(taskUuid, e.getMessage());
        }
    }

    /**
     * 人工复审：初评完成→复评中，携带初评报告再次评审
     */
    public CrReviewTask startReReview(String taskUuid, String operator) {
        CrReviewTask task = reportService.byUuid(taskUuid);
        if (task == null) {
            throw new IllegalArgumentException("评审任务不存在: " + taskUuid);
        }
        if (!"FIRST_REVIEW_DONE".equals(task.getStatus())) {
            throw new IllegalStateException("仅初评完成的任务可发起复审: " + task.getStatus());
        }
        task.setStatus("RE_REVIEWING");
        task.setTriggerBy(operator);
        taskMapper.updateById(task);
        dispatchAsync(taskUuid);
        return task;
    }

    /**
     * 任务关闭：复评完成→任务关闭
     */
    public CrReviewTask close(String taskUuid) {
        CrReviewTask task = reportService.byUuid(taskUuid);
        if (task == null) {
            throw new IllegalArgumentException("评审任务不存在: " + taskUuid);
        }
        if (!"RE_REVIEW_DONE".equals(task.getStatus()) && !"FAILED".equals(task.getStatus())) {
            throw new IllegalStateException("仅复评完成或失败的任务可关闭: " + task.getStatus());
        }
        task.setStatus("CLOSED");
        taskMapper.updateById(task);
        return task;
    }

    public List<CrReviewTask> list(String status) {
        return taskMapper.selectList(new LambdaQueryWrapper<CrReviewTask>()
                .eq(status != null && !status.isBlank(), CrReviewTask::getStatus, status)
                .orderByDesc(CrReviewTask::getId));
    }

    /**
     * WEBHOOK入口：按规则匹配仓库与分支
     */
    public List<CrReviewTask> onWebhook(String repoCode, String branch, String revision) {
        CrRepository repo = repositoryMapper.selectOne(new LambdaQueryWrapper<CrRepository>()
                .eq(CrRepository::getRepoCode, repoCode));
        if (repo == null) {
            return List.of();
        }
        List<CrReviewRule> rules = ruleMapper.selectList(new LambdaQueryWrapper<CrReviewRule>()
                .eq(CrReviewRule::getTriggerType, "WEBHOOK")
                .eq(CrReviewRule::getEnabled, 1));
        List<CrReviewTask> created = new java.util.ArrayList<>();
        for (CrReviewRule rule : rules) {
            if (rule.getRepoIds() != null && !rule.getRepoIds().contains(String.valueOf(repo.getId()))) {
                continue;
            }
            if (rule.getBranchFilter() != null && !rule.getBranchFilter().isBlank()
                    && !branch.matches(rule.getBranchFilter().replace("*", ".*"))) {
                continue;
            }
            CrReviewTask task = createTask(repo.getId(), branch, revision, "WEBHOOK", "webhook", rule.getId());
            dispatchAsync(task.getTaskUuid());
            created.add(task);
        }
        return created;
    }

    /**
     * 定期扫描：每小时触发启用的SCHEDULED规则
     */
    @Scheduled(cron = "${hermes.review.scan-cron:0 0 * * * ?}")
    public void scheduledScan() {
        List<CrReviewRule> rules = ruleMapper.selectList(new LambdaQueryWrapper<CrReviewRule>()
                .eq(CrReviewRule::getTriggerType, "SCHEDULED")
                .eq(CrReviewRule::getEnabled, 1));
        for (CrReviewRule rule : rules) {
            for (Long repoId : parseRepoIds(rule.getRepoIds())) {
                CrReviewTask task = createTask(repoId, null, null, "SCHEDULED", "scheduler", rule.getId());
                dispatchAsync(task.getTaskUuid());
            }
        }
    }

    private List<Long> parseRepoIds(String repoIdsJson) {
        List<Long> ids = new java.util.ArrayList<>();
        if (repoIdsJson == null || repoIdsJson.isBlank()) {
            return ids;
        }
        for (String part : repoIdsJson.replaceAll("[^0-9,]", "").split(",")) {
            if (!part.isBlank()) {
                ids.add(Long.parseLong(part));
            }
        }
        return ids;
    }

    private String lastReportExcerpt(CrReviewTask task) {
        if (task.getLastReportId() == null) {
            return null;
        }
        var report = reportService.reports(task.getTaskUuid()).stream()
                .filter(r -> r.getId().equals(task.getLastReportId()))
                .findFirst().orElse(null);
        if (report == null || report.getReportMarkdown() == null) {
            return null;
        }
        String md = report.getReportMarkdown();
        return md.length() > 4000 ? md.substring(0, 4000) : md;
    }

    private String buildExtraContext(CrRepository repo, CrReviewTask task,
                                     String commitInfo, String lastReport) {
        StringBuilder sb = new StringBuilder();
        sb.append("评审任务ID: ").append(task.getTaskUuid()).append('\n');
        sb.append("仓库: ").append(repo.getName()).append(" (").append(repo.getRepoUrl()).append(")\n");
        sb.append("分支: ").append(task.getBranch()).append('\n');
        sb.append("评审区间: ").append(task.getStartRevision()).append("..").append(task.getEndRevision()).append('\n');
        if (repo.getReviewPromptExtra() != null && !repo.getReviewPromptExtra().isBlank()) {
            sb.append("\n## 仓库级评审提示词\n").append(repo.getReviewPromptExtra()).append('\n');
        }
        if (lastReport != null) {
            sb.append("\n## 上次审查报告（节选）\n").append(lastReport).append('\n');
        }
        sb.append("\n").append(commitInfo);
        return sb.toString();
    }
}
