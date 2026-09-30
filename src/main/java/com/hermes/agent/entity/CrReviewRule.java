package com.hermes.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hermes.agent.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 评审规则：触发方式/参与仓库/参与人员
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cr_review_rule")
public class CrReviewRule extends BaseEntity {

    private String ruleCode;

    private String name;

    /** WEBHOOK/SCHEDULED/MANUAL */
    private String triggerType;

    private String cronExpr;

    /** 参与仓库ID列表（JSON） */
    private String repoIds;

    private String branchFilter;

    /** 参与人员ID列表（JSON） */
    private String participantIds;

    private Integer enabled;
}
