package com.hermes.agent.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentRunResult {

    private String reply;

    @Builder.Default
    private List<ToolEvent> toolEvents = new ArrayList<>();

    /** FIXED_FLOW 运行ID */
    private String runId;

    private boolean fixedFlow;

    /** 本轮是否被中断（/stop、/new、interrupt API → ADR-012） */
    private boolean interrupted;

    @Data
    @AllArgsConstructor
    public static class ToolEvent {
        private String toolCode;
        private boolean success;
        private String errorMessage;
    }
}
