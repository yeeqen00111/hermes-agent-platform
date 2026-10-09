package com.hermes.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hermes.agent.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 业务指标监控（白板·业务层·智能运维）：业务指标埋点 / 所属系统 / 指标类型。
 * 可配阈值与告警去向；采样命中阈值时落告警记录（alertType=METRIC）并经通知网关发送。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_biz_metric")
public class BizMetric extends BaseEntity {

    private String code;

    private String name;

    /** 业务指标埋点 */
    private String metricPoint;

    /** 所属系统 */
    private String systemName;

    /** 指标类型 */
    private String metricType;

    private String unit;

    /** 取数引用（埋点 key / SQL / 接口） */
    private String sourceRef;

    /** 阈值运算符：> >= < <= == */
    private String thresholdOp;

    private Double thresholdValue;

    /** 告警去向通道 */
    private String channelCode;

    private String recipient;

    private Integer enabled;

    private String remark;
}
