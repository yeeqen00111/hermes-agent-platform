package com.hermes.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 业务指标采样（只写不改不删，与 ai_alert_record / ai_tool_call 同模式）。
 */
@Data
@TableName("ai_biz_metric_sample")
public class BizMetricSample {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String metricCode;

    private Double metricValue;

    /** 是否命中阈值 */
    private Integer breached;

    private LocalDateTime sampleTime;

    private LocalDateTime createTime;
}
