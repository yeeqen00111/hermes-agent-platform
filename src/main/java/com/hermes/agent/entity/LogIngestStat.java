package com.hermes.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 日志接收/过滤统计（白板·智能运维·告警监控）：按项目记录上次接收过滤时间与告警/提级/拦截次数。
 */
@Data
@TableName("ai_log_ingest_stat")
public class LogIngestStat {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String projectName;

    /** 上次接收过滤时间 */
    private LocalDateTime lastIngestTime;

    private Integer totalIngested;

    private Integer totalAlert;

    private Integer totalPromote;

    private Integer totalSuppress;

    private LocalDateTime updateTime;
}
