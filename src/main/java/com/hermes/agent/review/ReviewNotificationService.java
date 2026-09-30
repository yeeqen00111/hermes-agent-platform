package com.hermes.agent.review;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hermes.agent.entity.AiNotifyTemplate;
import com.hermes.agent.entity.CrReviewParticipant;
import com.hermes.agent.entity.CrReviewTask;
import com.hermes.agent.mapper.AiNotifyTemplateMapper;
import com.hermes.agent.notify.NotificationGateway;
import com.hermes.agent.notify.TemplateRenderer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 评审通知：报告完成/失败时通知规则里的「接收报告」人员。
 * 发送走 NotificationGateway（飞书/邮件，◆ 复用控制塔告警通道的平台侧集成位）；
 * 模板缺省时用内置默认文案，通道未配置时网关落 NO_CHANNEL 日志。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewNotificationService {

    private static final String TPL_READY = "review-report-ready";
    private static final String TPL_FAILED = "review-report-failed";
    private static final String DEFAULT_READY_TITLE = "代码评审报告已生成";
    private static final String DEFAULT_READY_CONTENT = "评审任务 {{taskUuid}} 第 {{round}} 轮报告已生成，状态 {{status}}，请登录平台查看。";
    private static final String DEFAULT_FAILED_TITLE = "代码评审任务失败";
    private static final String DEFAULT_FAILED_CONTENT = "评审任务 {{taskUuid}} 执行失败: {{error}}";

    private final ReviewParticipantService participantService;
    private final NotificationGateway gateway;
    private final TemplateRenderer renderer;
    private final AiNotifyTemplateMapper templateMapper;

    public void notifyReportReady(CrReviewTask task, int round) {
        notify(task, TPL_READY,
                renderTemplate(TPL_READY, DEFAULT_READY_TITLE, DEFAULT_READY_CONTENT,
                        Map.of("taskUuid", task.getTaskUuid(), "round", round, "status", task.getStatus())));
    }

    public void notifyFailed(CrReviewTask task, String errorMessage) {
        notify(task, TPL_FAILED,
                renderTemplate(TPL_FAILED, DEFAULT_FAILED_TITLE, DEFAULT_FAILED_CONTENT,
                        Map.of("taskUuid", task.getTaskUuid(), "error", errorMessage)));
    }

    private void notify(CrReviewTask task, String templateCode, String[] titleAndContent) {
        List<CrReviewParticipant> recipients = participantService.recipients(task);
        if (recipients.isEmpty()) {
            log.info("评审通知无接收人（规则未配置 RECIPIENT 或任务未绑定规则）: task={}", task.getTaskUuid());
            return;
        }
        String title = titleAndContent[0];
        String content = titleAndContent[1];
        for (CrReviewParticipant p : recipients) {
            if (p.getEmail() != null && !p.getEmail().isBlank()) {
                gateway.sendByType("EMAIL", p.getEmail(), title, content);
            }
            if (p.getFeishu() != null && !p.getFeishu().isBlank()) {
                gateway.sendByType("FEISHU", p.getFeishu(), title, content);
            }
            if ((p.getEmail() == null || p.getEmail().isBlank())
                    && (p.getFeishu() == null || p.getFeishu().isBlank())) {
                log.warn("评审通知接收人未配置联系方式，跳过: userId={}, template={}", p.getUserId(), templateCode);
            }
        }
    }

    private String[] renderTemplate(String code, String defaultTitle, String defaultContent,
                                    Map<String, Object> vars) {
        AiNotifyTemplate tpl = templateMapper.selectOne(new LambdaQueryWrapper<AiNotifyTemplate>()
                .eq(AiNotifyTemplate::getCode, code)
                .eq(AiNotifyTemplate::getEnabled, 1));
        if (tpl == null) {
            return new String[]{renderer.render(defaultTitle, vars), renderer.render(defaultContent, vars)};
        }
        String title = tpl.getTitleTemplate() == null || tpl.getTitleTemplate().isBlank()
                ? defaultTitle : tpl.getTitleTemplate();
        String content = tpl.getContentTemplate() == null || tpl.getContentTemplate().isBlank()
                ? defaultContent : tpl.getContentTemplate();
        return new String[]{renderer.render(title, vars), renderer.render(content, vars)};
    }
}
