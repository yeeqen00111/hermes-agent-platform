package com.hermes.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hermes.agent.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 评审规则参与人：三角色（可发起/接收报告/可执行闭环）+ 联系方式
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cr_review_participant")
public class CrReviewParticipant extends BaseEntity {

    private Long ruleId;

    private Long userId;

    /** INITIATOR / RECIPIENT / CLOSER */
    private String role;

    private String email;

    private String feishu;
}
