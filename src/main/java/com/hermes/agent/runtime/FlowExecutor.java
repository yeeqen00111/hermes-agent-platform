package com.hermes.agent.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hermes.agent.dto.ToolRequest;
import com.hermes.agent.dto.ToolResponse;
import com.hermes.agent.entity.FlowRun;
import com.hermes.agent.entity.FlowStepLog;
import com.hermes.agent.llm.LlmGateway;
import com.hermes.agent.llm.LlmMessage;
import com.hermes.agent.mapper.FlowRunMapper;
import com.hermes.agent.mapper.FlowStepLogMapper;
import com.hermes.agent.persona.PersonaPack;
import com.hermes.agent.tool.ToolExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 固定流程编排执行器：确定性步骤流水线（TOOL/LLM），
 * 每步落库状态/耗时/上下游/错误原因，供看板监控与告警使用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowExecutor {

    private final FlowRunMapper flowRunMapper;
    private final FlowStepLogMapper stepLogMapper;
    private final ToolExecutor toolExecutor;
    private final LlmGateway llmGateway;
    private final ObjectMapper objectMapper;

    public AgentRunResult execute(PersonaPack pack, AgentRunRequest request) {
        FlowRun run = new FlowRun();
        run.setRunId(UUID.randomUUID().toString());
        run.setAgentCode(pack.getAgentCode());
        run.setBizKey(request.getBizKey());
        run.setStatus("RUNNING");
        run.setInput(request.getUserInput());
        run.setStartTime(LocalDateTime.now());
        flowRunMapper.insert(run);

        AgentRunResult result = AgentRunResult.builder().fixedFlow(true).runId(run.getRunId()).build();
        try {
            JsonNode flow = objectMapper.readTree(pack.getProfile().getFlowDefinition());
            JsonNode steps = flow.path("steps");
            Map<String, String> outputs = new HashMap<>();
            String prev = request.getUserInput() == null ? "" : request.getUserInput();
            String lastOutput = prev;

            for (JsonNode step : steps) {
                String code = step.path("code").asText();
                String upstream = step.path("upstream").asText("");
                String stepInput = upstream.isEmpty() ? prev : outputs.getOrDefault(upstream, prev);
                FlowStepLog stepLog = startStep(run.getRunId(), step, upstream);
                try {
                    String output;
                    if ("TOOL".equalsIgnoreCase(step.path("type").asText())) {
                        output = runToolStep(pack, request, step, stepInput, result);
                    } else {
                        output = runLlmStep(pack, step, stepInput, request);
                    }
                    outputs.put(code, output);
                    lastOutput = output;
                    prev = output;
                    finishStep(stepLog, "SUCCESS", output, null);
                } catch (Exception e) {
                    finishStep(stepLog, "FAILED", null, e.getMessage());
                    throw e;
                }
            }

            run.setStatus("SUCCESS");
            run.setOutput(lastOutput);
            result.setReply(lastOutput);
        } catch (Exception e) {
            log.error("固定流程执行失败: agent={}, run={}", pack.getAgentCode(), run.getRunId(), e);
            run.setStatus("FAILED");
            run.setErrorMessage(truncate(e.getMessage()));
            result.setReply("流程执行失败: " + e.getMessage());
        } finally {
            run.setEndTime(LocalDateTime.now());
            run.setDurationMs(Duration.between(run.getStartTime(), run.getEndTime()).toMillis());
            flowRunMapper.updateById(run);
        }
        return result;
    }

    private String runToolStep(PersonaPack pack, AgentRunRequest request, JsonNode step,
                               String stepInput, AgentRunResult result) {
        String toolCode = step.path("toolCode").asText();
        ToolRequest tr = new ToolRequest();
        tr.setTraceId(request.getTraceId());
        tr.setSessionId(request.getSessionId());
        tr.setAgentCode(pack.getAgentCode());
        tr.setUserId(request.getUserId());
        Map<String, Object> args = new HashMap<>();
        args.put("input", stepInput);
        tr.setArguments(args);
        ToolResponse resp = toolExecutor.execute(toolCode, tr);
        result.getToolEvents().add(new AgentRunResult.ToolEvent(
                toolCode, Boolean.TRUE.equals(resp.getSuccess()), resp.getErrorMessage()));
        if (!Boolean.TRUE.equals(resp.getSuccess())) {
            throw new IllegalStateException("工具执行失败: " + resp.getErrorCode() + " " + resp.getErrorMessage());
        }
        try {
            return objectMapper.writeValueAsString(resp.getData());
        } catch (Exception e) {
            return String.valueOf(resp.getData());
        }
    }

    private String runLlmStep(PersonaPack pack, JsonNode step, String stepInput, AgentRunRequest request) {
        String prompt = step.path("promptTemplate").asText("{{prev}}")
                .replace("{{prev}}", stepInput)
                .replace("{{input}}", request.getUserInput() == null ? "" : request.getUserInput());
        return llmGateway.chat(pack.getProfile(), java.util.List.of(
                LlmMessage.system(pack.getSystemPrompt()),
                LlmMessage.user(prompt)));
    }

    private FlowStepLog startStep(String runId, JsonNode step, String upstream) {
        FlowStepLog stepLog = new FlowStepLog();
        stepLog.setRunId(runId);
        stepLog.setStepCode(step.path("code").asText());
        stepLog.setStepName(step.path("name").asText(step.path("code").asText()));
        stepLog.setStepType(step.path("type").asText());
        stepLog.setUpstreamStep(upstream.isEmpty() ? null : upstream);
        stepLog.setStatus("RUNNING");
        stepLog.setStartTime(LocalDateTime.now());
        stepLogMapper.insert(stepLog);
        return stepLog;
    }

    private void finishStep(FlowStepLog stepLog, String status, String output, String error) {
        stepLog.setStatus(status);
        stepLog.setOutput(truncate(output));
        stepLog.setErrorMessage(truncate(error));
        stepLog.setEndTime(LocalDateTime.now());
        stepLog.setDurationMs(Duration.between(stepLog.getStartTime(), stepLog.getEndTime()).toMillis());
        stepLogMapper.updateById(stepLog);
    }

    private String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() > 8000 ? s.substring(0, 8000) : s;
    }
}
