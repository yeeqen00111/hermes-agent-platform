package com.hermes.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评审问题（复审闭环）
 */
@Data
@TableName("cr_review_issue")
public class CrReviewIssue {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String taskUuid;

    private Long reportId;

    /** BLOCKER/CRITICAL/MAJOR/MINOR */
    private String severity;

    private String category;

    private String title;

    private String filePath;

    private Integer lineNo;

    /** OPEN/FIXED/WONT_FIX/CLOSED */
    private String status;

    private String recheckNote;

    @TableLogic
    private Integer delFlag;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
