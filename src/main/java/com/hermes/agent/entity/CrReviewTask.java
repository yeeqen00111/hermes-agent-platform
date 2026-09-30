package com.hermes.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hermes.agent.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 评审任务（状态机：PENDING→REVIEWING→FIRST_REVIEW_DONE→RE_REVIEWING→RE_REVIEW_DONE→CLOSED，失败FAILED）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cr_review_task")
public class CrReviewTask extends BaseEntity {

    private String taskUuid;

    private Long ruleId;

    private Long repoId;

    private String branch;

    private String startRevision;

    private String endRevision;

    private String status;

    private String triggerType;

    private String triggerBy;

    private Long lastReportId;

    private java.time.LocalDateTime dispatchTime;

    private java.time.LocalDateTime finishTime;

    private String errorMessage;
}
