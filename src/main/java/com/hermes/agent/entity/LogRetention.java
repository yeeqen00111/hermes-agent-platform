package com.hermes.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 日志分级保留策略（白板·智能运维）：全量 3 天 / 告警 7 天 / 提级告警 7 天。
 */
@Data
@TableName("ai_log_retention")
public class LogRetention {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** FULL（全量）/ ALERT（告警）/ PROMOTE（提级告警） */
    private String dataType;

    private Integer retentionDays;

    private Integer enabled;

    private String remark;
}
