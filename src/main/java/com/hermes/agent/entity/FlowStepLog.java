package com.hermes.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 固定流程步骤日志：状态/耗时/上下游/错误原因
 */
@Data
@TableName("ai_flow_step_log")
public class FlowStepLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String runId;

    private String stepCode;

    private String stepName;

    /** TOOL/LLM */
    private String stepType;

    private String upstreamStep;

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
