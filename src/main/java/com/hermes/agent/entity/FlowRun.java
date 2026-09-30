package com.hermes.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 固定流程运行记录
 */
@Data
@TableName("ai_flow_run")
public class FlowRun {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String runId;

    private String agentCode;

    /** 业务键（如评审任务UUID） */
    private String bizKey;

    /** RUNNING/SUCCESS/FAILED */
    private String status;

    private String input;

    private String output;

    private String errorMessage;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Long durationMs;

    private LocalDateTime createTime;
}
