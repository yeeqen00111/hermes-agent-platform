package com.hermes.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hermes.agent.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 运维经验库（白板·Agent 层·运维智能体「自我进化」）：
 * 把「问题 → 解决方案」沉淀成可复用经验，靠复用/反馈强化、靠定时任务衰减淘汰，形成进化闭环。
 * <p>对应白板：输入基础设定，基于日志/代码库/数据库，输出问题到解决方案，辅助运维。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_ops_experience")
public class OpsExperience extends BaseEntity {

    private String expCode;

    private String agentCode;

    /** 问题/症状 */
    private String problem;

    /** 问题指纹（去重归并用） */
    private String problemKey;

    private String cause;

    /** 处置方案 */
    private String solution;

    /** 标签（逗号分隔） */
    private String tags;

    private String systemName;

    /** ALERT / CHAT / MANUAL / KB */
    private String sourceType;

    private String sourceRef;

    /** 复用次数 */
    private Integer hits;

    private Integer successCount;

    private Integer failCount;

    /** 置信度 0~1 */
    private Double confidence;

    /** ACTIVE / DEPRECATED */
    private String status;

    private LocalDateTime lastUsedTime;
}
