package com.hermes.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审批白名单：allow_always 的持久化结果，跨会话生效
 */
@Data
@TableName("ai_approval_whitelist")
public class ApprovalWhitelist {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String toolCode;

    /** 生效用户，0=全部用户 */
    private Long userId;

    /** 生效身份包，空=全部 */
    private String agentCode;

    /** 谁开的 */
    private Long grantedBy;

    /** 什么时候开的 */
    private LocalDateTime grantTime;

    private Integer enabled;
}
