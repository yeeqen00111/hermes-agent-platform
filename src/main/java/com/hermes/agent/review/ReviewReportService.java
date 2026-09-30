package com.hermes.agent.review;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hermes.agent.entity.CrReviewIssue;
import com.hermes.agent.entity.CrReviewReport;
import com.hermes.agent.entity.CrReviewTask;
import com.hermes.agent.mapper.CrReviewIssueMapper;
import com.hermes.agent.mapper.CrReviewReportMapper;
import com.hermes.agent.mapper.CrReviewTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 评审报告回传：Agent（含失败/超时）统一经此回调，平台据此推进状态机
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewReportService {

    private static final Pattern JSON_FENCE = Pattern.compile("```json\\s*(\\{.*?})\\s*```", Pattern.DOTALL);

    private final CrReviewTaskMapper taskMapper;
    private final CrReviewReportMapper reportMapper;
    private final CrReviewIssueMapper issueMapper;
    private final ObjectMapper objectMapper;

    @Transactional
    public CrReviewReport upload(String taskUuid, String markdown, String scores,
                                 String issuesJson, String modelName) {
        CrReviewTask task = byUuid(taskUuid);
        if (task == null) {
            throw new IllegalArgumentException("评审任务不存在: " + taskUuid);
        }
        if (!"REVIEWING".equals(task.getStatus()) && !"RE_REVIEWING".equals(task.getStatus())) {
            throw new IllegalStateException("任务当前状态不允许回传报告: " + task.getStatus());
        }
        int round = "RE_REVIEWING".equals(task.getStatus()) ? 2 : 1;

        String scoresJson = scores;
        String issues = issuesJson;
        if (scoresJson == null || issues == null) {
            JsonNode parsed = parseStructured(markdown);
            if (parsed != null) {
                if (scoresJson == null) {
                    scoresJson = parsed.path("scores").toString();
                }
                if (issues == null) {
                    issues = parsed.path("issues").toString();
                }
            }
        }

        CrReviewReport report = new CrReviewReport();
        report.setTaskUuid(taskUuid);
        report.setReportMarkdown(markdown);
        report.setScores(scoresJson);
        report.setModelName(modelName);
        report.setReviewRound(round);
        reportMapper.insert(report);

        saveIssues(taskUuid, report.getId(), issues);

        task.setStatus(round == 1 ? "FIRST_REVIEW_DONE" : "RE_REVIEW_DONE");
        task.setLastReportId(report.getId());
        task.setFinishTime(LocalDateTime.now());
        task.setErrorMessage(null);
        taskMapper.updateById(task);
        log.info("评审报告已回传: task={}, round={}", taskUuid, round);
        return report;
    }

    /**
     * 失败回调：超时/报错也必须调用，任务置FAILED并记录原因
     */
    public void failureCallback(String taskUuid, String errorMessage) {
        CrReviewTask task = byUuid(taskUuid);
        if (task == null) {
            log.warn("失败回调指向不存在的任务: {}", taskUuid);
            return;
        }
        task.setStatus("FAILED");
        task.setErrorMessage(errorMessage);
        task.setFinishTime(LocalDateTime.now());
        taskMapper.updateById(task);
        log.warn("评审任务失败回调: task={}, error={}", taskUuid, errorMessage);
    }

    public CrReviewTask byUuid(String taskUuid) {
        return taskMapper.selectOne(new LambdaQueryWrapper<CrReviewTask>()
                .eq(CrReviewTask::getTaskUuid, taskUuid));
    }

    public CrReviewReport latestReport(String taskUuid) {
        return reportMapper.selectOne(new LambdaQueryWrapper<CrReviewReport>()
                .eq(CrReviewReport::getTaskUuid, taskUuid)
                .orderByDesc(CrReviewReport::getId)
                .last("LIMIT 1"));
    }

    public List<CrReviewReport> reports(String taskUuid) {
        return reportMapper.selectList(new LambdaQueryWrapper<CrReviewReport>()
                .eq(CrReviewReport::getTaskUuid, taskUuid)
                .orderByAsc(CrReviewReport::getId));
    }

    public List<CrReviewIssue> issues(String taskUuid) {
        return issueMapper.selectList(new LambdaQueryWrapper<CrReviewIssue>()
                .eq(CrReviewIssue::getTaskUuid, taskUuid)
                .orderByAsc(CrReviewIssue::getId));
    }

    public void updateIssue(CrReviewIssue issue) {
        issueMapper.updateById(issue);
    }

    private void saveIssues(String taskUuid, Long reportId, String issuesJson) {
        if (issuesJson == null || issuesJson.isBlank() || !issuesJson.trim().startsWith("[")) {
            return;
        }
        try {
            JsonNode arr = objectMapper.readTree(issuesJson);
            for (JsonNode n : arr) {
                CrReviewIssue issue = new CrReviewIssue();
                issue.setTaskUuid(taskUuid);
                issue.setReportId(reportId);
                issue.setSeverity(n.path("severity").asText("MAJOR"));
                issue.setCategory(n.path("category").asText(null));
                issue.setTitle(n.path("title").asText("未命名问题"));
                issue.setFilePath(n.path("file").asText(null));
                issue.setLineNo(n.path("line").asInt(0));
                issue.setStatus("OPEN");
                issueMapper.insert(issue);
            }
        } catch (Exception e) {
            log.warn("评审问题解析失败: {}", e.getMessage());
        }
    }

    private JsonNode parseStructured(String markdown) {
        if (markdown == null) {
            return null;
        }
        Matcher m = JSON_FENCE.matcher(markdown);
        JsonNode last = null;
        while (m.find()) {
            try {
                JsonNode node = objectMapper.readTree(m.group(1));
                if (node.has("scores") || node.has("issues")) {
                    last = node;
                }
            } catch (Exception ignored) {
                // 非结构化json块跳过
            }
        }
        return last;
    }
}
