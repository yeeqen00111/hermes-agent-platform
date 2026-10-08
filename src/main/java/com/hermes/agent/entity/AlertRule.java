package com.hermes.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hermes.agent.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 日志告警规则（白板·业务层·智能运维）：
 * 分项目进行配置，过滤规则分三类——该告警（ALERT）/ 不告警（WHITELIST）/ 提级告警（PROMOTE），
 * 并指明告警发送去向（通道 + 接收人）。固化流程按 priority 依次判定：1 告警规则 → 2 白名单 → 3 提级。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_alert_rule")
public class AlertRule extends BaseEntity {

    private String code;

    private String name;

    /** 所属项目（一个项目一套配置） */
    private String projectName;

    /** ALERT（该告警）/ WHITELIST（不告警）/ PROMOTE（提级告警） */
    private String ruleType;

    /** 固化优先级：1 告警规则 / 2 白名单 / 3 提级 */
    private Integer priority;

    /** 日志匹配（正则，空=全部） */
    private String matchPattern;

    /** 日志级别过滤（逗号分隔，空=不限） */
    private String levelFilter;

    /** 告警去向通道编码 */
    private String channelCode;

    /** 接收人（邮箱/飞书账号） */
    private String recipient;

    private Integer enabled;

    private String remark;
}
