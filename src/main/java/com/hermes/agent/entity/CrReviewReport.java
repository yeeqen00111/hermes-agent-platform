package com.hermes.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评审报告（markdown + 多维评分）
 */
@Data
@TableName("cr_review_report")
public class CrReviewReport {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String taskUuid;

    private String reportMarkdown;

    /** 多维评分JSON：健壮性/BUG/安全/可维护性/性能 */
    private String scores;

    private String modelName;

    private Integer reviewRound;

    private LocalDateTime createTime;
}
