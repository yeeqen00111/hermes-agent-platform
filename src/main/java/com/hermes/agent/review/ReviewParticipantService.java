package com.hermes.agent.review;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hermes.agent.entity.CrReviewParticipant;
import com.hermes.agent.entity.CrReviewRule;
import com.hermes.agent.entity.CrReviewTask;
import com.hermes.agent.mapper.CrReviewParticipantMapper;
import com.hermes.agent.mapper.CrReviewRuleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 评审规则参与人（白板「库的权限、人员配置」）：
 * 三角色 INITIATOR（可发起）/ RECIPIENT（接收报告）/ CLOSER（可执行闭环），人员需邮箱/飞书。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewParticipantService {

    private static final Set<String> ROLES = Set.of("INITIATOR", "RECIPIENT", "CLOSER");

    private final CrReviewParticipantMapper participantMapper;
    private final CrReviewRuleMapper ruleMapper;

    public List<CrReviewParticipant> list(Long ruleId) {
        return participantMapper.selectList(new LambdaQueryWrapper<CrReviewParticipant>()
                .eq(CrReviewParticipant::getRuleId, ruleId)
                .orderByAsc(CrReviewParticipant::getId));
    }

    public CrReviewParticipant save(CrReviewParticipant participant) {
        if (participant.getRuleId() == null || participant.getUserId() == null) {
            throw new IllegalArgumentException("ruleId 与 userId 必填");
        }
        if (participant.getRole() == null || !ROLES.contains(participant.getRole().toUpperCase())) {
            throw new IllegalArgumentException("role 必须为 INITIATOR / RECIPIENT / CLOSER");
        }
        participant.setRole(participant.getRole().toUpperCase());
        if (participant.getId() != null) {
            participantMapper.updateById(participant);
        } else {
            participantMapper.insert(participant);
        }
        return participant;
    }

    public void delete(Long id) {
        participantMapper.deleteById(id);
    }

    /** 任务对应规则的「接收报告」人员，用于报告完成/失败通知 */
    public List<CrReviewParticipant> recipients(CrReviewTask task) {
        if (task.getRuleId() == null) {
            return List.of();
        }
        return participantMapper.selectList(new LambdaQueryWrapper<CrReviewParticipant>()
                .eq(CrReviewParticipant::getRuleId, task.getRuleId())
                .eq(CrReviewParticipant::getRole, "RECIPIENT"));
    }

    /**
     * 用户参与的规则ID：参与人表任意角色 + 规则的 participant_ids（历史扁平名单）
     */
    public List<Long> ruleIdsOfUser(Long userId) {
        List<Long> ruleIds = new ArrayList<>();
        participantMapper.selectList(new LambdaQueryWrapper<CrReviewParticipant>()
                        .eq(CrReviewParticipant::getUserId, userId))
                .forEach(p -> ruleIds.add(p.getRuleId()));
        ruleMapper.selectList(null).stream()
                .filter(r -> containsLegacyUserId(r.getParticipantIds(), userId))
                .forEach(r -> ruleIds.add(r.getId()));
        return ruleIds.stream().distinct().toList();
    }

    private boolean containsLegacyUserId(String participantIds, Long userId) {
        if (participantIds == null || participantIds.isBlank()) {
            return false;
        }
        for (String part : participantIds.replaceAll("[^0-9,]", "").split(",")) {
            if (!part.isBlank() && Long.parseLong(part) == userId) {
                return true;
            }
        }
        return false;
    }
}
