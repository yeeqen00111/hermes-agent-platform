package com.hermes.agent.review;

import com.hermes.agent.entity.CrReviewParticipant;
import com.hermes.agent.entity.CrReviewTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 评审通知：报告完成/失败时通知规则里的「接收报告」人员。
 * 实际发送走 ◆ 复用供应链控制塔的告警通道（飞书/邮件）——迁移项，此处为挂接点。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewNotificationService {

    private final ReviewParticipantService participantService;

    public void notifyReportReady(CrReviewTask task, int round) {
        notify(task, "评审报告已生成（第" + round + "轮）: task=" + task.getTaskUuid()
                + ", status=" + task.getStatus());
    }

    public void notifyFailed(CrReviewTask task, String errorMessage) {
        notify(task, "评审任务失败: task=" + task.getTaskUuid()
                + ", error=" + errorMessage);
    }

    private void notify(CrReviewTask task, String content) {
        List<CrReviewParticipant> recipients = participantService.recipients(task);
        if (recipients.isEmpty()) {
            log.info("评审通知无接收人（规则未配置 RECIPIENT 或任务未绑定规则）: task={}", task.getTaskUuid());
            return;
        }
        for (CrReviewParticipant p : recipients) {
            // TODO 迁移项：◆ 复用控制塔告警通道（飞书/邮件）真正发送，此处仅记录待发
            log.info("评审通知[stub] -> userId={}, email={}, feishu={}, content={}",
                    p.getUserId(), p.getEmail(), p.getFeishu(), content);
        }
    }
}
