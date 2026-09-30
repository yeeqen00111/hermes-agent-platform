package com.hermes.agent.review;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hermes.agent.entity.CrReviewParticipant;
import com.hermes.agent.entity.CrReviewTask;
import com.hermes.agent.mapper.CrReviewParticipantMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * 三角色权限强制（白板「哪些人可以发起 / 哪些人可以执行闭环」）：
 * 发起/复审需 INITIATOR，闭环（任务关闭、问题状态流转）需 CLOSER。
 * 身份来源 X-Actor-User-Id（三期 ◆ 控制塔身份体系的集成位，见 ADR-011）：
 * 请求头缺席 = 系统通道（webhook/定时/内部调用）放行并告警；携带则强制校验。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewPermissionService {

    private final CrReviewParticipantMapper participantMapper;

    /** ruleId 为空 = 任务未绑定规则，无参与人约束，放行 */
    public void requireRole(Long ruleId, Long actorUserId, String role) {
        if (ruleId == null) {
            return;
        }
        if (actorUserId == null) {
            log.warn("权限校验: 请求未携带 X-Actor-User-Id，按系统通道放行（ruleId={}, role={}）", ruleId, role);
            return;
        }
        Long count = participantMapper.selectCount(new LambdaQueryWrapper<CrReviewParticipant>()
                .eq(CrReviewParticipant::getRuleId, ruleId)
                .eq(CrReviewParticipant::getRole, role)
                .eq(CrReviewParticipant::getUserId, actorUserId));
        if (count == null || count == 0) {
            log.warn("权限拒绝: 用户 {} 不是规则 {} 的 {}，操作被拦截", actorUserId, ruleId, role);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "无权限: 用户 " + actorUserId + " 不是规则 " + ruleId + " 的 " + role);
        }
    }

    public void requireTaskRole(CrReviewTask task, Long actorUserId, String role) {
        if (task == null) {
            return; // 任务不存在的报错由下游服务给出
        }
        requireRole(task.getRuleId(), actorUserId, role);
    }
}
