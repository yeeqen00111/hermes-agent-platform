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
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 固定流程编排执行器：确定性步骤流水线（TOOL/LLM），
 * 每步落库状态/耗时/上下游/错误原因。
 * TOOL 步骤入参来源：静态 arguments → argsFromUpstream（上游输出 JSON）→ {"input": 上游文本}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowExecutor {

    private static final DateTimeFormatter NOW_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final FlowRunMapper flowRunMapper;
    private final FlowStepLogMapper stepLogMapper;
    private final ToolExecutor toolExecutor;
    private final LlmGateway llmGateway;
    private final ObjectMapper objectMapper;

    public AgentRunResult execute(PersonaPack pack, AgentRunRequest request,
                                  Consumer<String> onDelta,
                                  Consumer<AgentRunResult.ToolEvent> onToolEvent,
                                  SessionCancellationRegistry.Handle cancelHandle) {
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
            int last = steps.isArray() ? steps.size() - 1 : -1;
            Map<String, String> outputs = new HashMap<>();
            String prev = request.getUserInput() == null ? "" : request.getUserInput();
            String lastOutput = prev;

            for (int i = 0; i <= last; i++) {
                JsonNode step = steps.get(i);
                if (cancelHandle != null && cancelHandle.isCancelled()) {
                    run.setStatus("INTERRUPTED");
                    result.setReply(AgentRuntime.INTERRUPTED_REPLY);
                    result.setInterrupted(true);
                    return result;
                }
                String code = step.path("code").asText();
                String upstream = step.path("upstream").asText("");
                String stepInput = upstream.isEmpty() ? prev : outputs.getOrDefault(upstream, prev);
                FlowStepLog stepLog = startStep(run.getRunId(), step, upstream);
                try {
                    String output;
                    if ("TOOL".equalsIgnoreCase(step.path("type").asText())) {
                        output = runToolStep(pack, request, step, stepInput, result, onToolEvent);
                    } else {
                        output = runLlmStep(pack, step, stepInput, request);
                        if (i == last && onDelta != null && !output.isEmpty()) {
                            for (int p = 0; p < output.length(); p += 16) {
                                onDelta.accept(output.substring(p, Math.min(p + 16, output.length())));
                            }
                        }
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
                               String stepInput, AgentRunResult result,
                               Consumer<AgentRunResult.ToolEvent> onToolEvent) {
        String toolCode = step.path("toolCode").asText();
        ToolRequest tr = new ToolRequest();
        tr.setTraceId(request.getTraceId());
        tr.setSessionId(request.getSessionId());
        tr.setAgentCode(pack.getAgentCode());
        tr.setUserId(request.getUserId());
        tr.setChannel("flow");
        tr.setDataScope(request.getDataScope());
        tr.setEnvironment(request.getEnvironment());
        Map<String, Object> args = new HashMap<>();
        JsonNode staticArgs = step.path("arguments");
        if (staticArgs.isObject()) {
            staticArgs.fields().forEachRemaining(f -> args.put(f.getKey(), jsonValue(f.getValue())));
        }
        if (step.path("argsFromUpstream").asBoolean(false)) {
            boolean merged = false;
            try {
                JsonNode up = objectMapper.readTree(stepInput);
                if (up != null && up.isObject()) {
                    up.fields().forEachRemaining(f -> args.put(f.getKey(), jsonValue(f.getValue())));
                    merged = true;
                }
            } catch (Exception ignore) {
                // 上游不是 JSON 对象时回退 {"input"}，让工具以缺参错误如实反馈
            }
            if (!merged) {
                args.putIfAbsent("input", stepInput);
            }
        }
        if (args.isEmpty()) {
            args.put("input", stepInput);
        }
        tr.setArguments(args);
        ToolResponse resp = toolExecutor.execute(toolCode, tr);
        AgentRunResult.ToolEvent event = new AgentRunResult.ToolEvent(
                toolCode, Boolean.TRUE.equals(resp.getSuccess()), resp.getErrorMessage());
        result.getToolEvents().add(event);
        if (onToolEvent != null) {
            onToolEvent.accept(event);
        }
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
                .replace("{{input}}", request.getUserInput() == null ? "" : request.getUserInput())
                .replace("{{now}}", LocalDateTime.now().format(NOW_FMT));
        return llmGateway.chat(pack.getProfile(), List.of(
                LlmMessage.system(pack.getSystemPrompt()),
                LlmMessage.user(prompt)));
    }

    /** JsonNode → 工具入参值：数值/布尔保型，其余转文本 */
    private Object jsonValue(JsonNode n) {
        if (n == null || n.isNull()) {
            return null;
        }
        if (n.isInt() || n.isLong()) {
            return n.asLong();
        }
        if (n.isBoolean()) {
            return n.asBoolean();
        }
        if (n.isDouble() || n.isFloat() || n.isBigDecimal()) {
            return n.asDouble();
        }
        if (n.isValueNode()) {
            return n.asText();
        }
        return n.toString();
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
