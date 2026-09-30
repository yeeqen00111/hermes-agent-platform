package com.hermes.agent.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hermes.agent.dto.ToolRequest;
import com.hermes.agent.dto.ToolResponse;
import com.hermes.agent.llm.LlmGateway;
import com.hermes.agent.llm.LlmMessage;
import com.hermes.agent.persona.PersonaAssembler;
import com.hermes.agent.persona.PersonaPack;
import com.hermes.agent.session.SessionManager;
import com.hermes.agent.tool.ToolExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 唯一智能体基座运行时。
 * 功能智能体（评审/运维/报表/问数）= 身份包(PersonaPack) + 执行模式：
 * LLM_DRIVEN 走 ReAct 循环，FIXED_FLOW 走确定性编排。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentRuntime {

    private static final Pattern TOOL_CALL = Pattern.compile("```tool_call\\s*(\\{.*?})\\s*```", Pattern.DOTALL);
    private static final int MAX_TOOL_ROUNDS = 4;

    private final PersonaAssembler personaAssembler;
    private final LlmGateway llmGateway;
    private final ToolExecutor toolExecutor;
    private final FlowExecutor flowExecutor;
    private final SessionManager sessionManager;
    private final ObjectMapper objectMapper;

    public AgentRunResult run(AgentRunRequest request) {
        return execute(request, null, null);
    }

    /**
     * 流式执行：onDelta 接收最终回答增量，onToolEvent 接收工具调用事件
     */
    public AgentRunResult stream(AgentRunRequest request, Consumer<String> onDelta,
                                 Consumer<AgentRunResult.ToolEvent> onToolEvent) {
        return execute(request, onDelta, onToolEvent);
    }

    private AgentRunResult execute(AgentRunRequest request, Consumer<String> onDelta,
                                   Consumer<AgentRunResult.ToolEvent> onToolEvent) {
        PersonaPack pack = personaAssembler.assemble(
                request.getAgentCode(), request.getUserId(), request.getSessionId());

        if (pack.isFixedFlow()) {
            return flowExecutor.execute(pack, request);
        }

        List<LlmMessage> messages = buildMessages(pack, request);
        AgentRunResult result = AgentRunResult.builder().build();

        for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
            boolean lastRound = round == MAX_TOOL_ROUNDS - 1;
            String reply = llmGateway.chat(pack.getProfile(), messages);

            Matcher m = TOOL_CALL.matcher(reply);
            if (!m.find() || lastRound) {
                String finalReply = m.find()
                        ? reply.replaceAll("```tool_call\\s*\\{.*?}\\s*```", "").trim()
                        : reply;
                result.setReply(finalReply);
                if (onDelta != null && finalReply != null && !finalReply.isEmpty()) {
                    emitChunks(finalReply, onDelta);
                }
                return result;
            }

            messages.add(LlmMessage.assistant(reply));
            String toolResultText = executeToolCall(m.group(1), request, result, onToolEvent);
            messages.add(LlmMessage.tool(toolResultText));
        }
        return result;
    }

    private void emitChunks(String text, Consumer<String> onDelta) {
        for (int i = 0; i < text.length(); i += 16) {
            onDelta.accept(text.substring(i, Math.min(i + 16, text.length())));
        }
    }

    private String executeToolCall(String json, AgentRunRequest request, AgentRunResult result,
                                   Consumer<AgentRunResult.ToolEvent> onToolEvent) {
        String toolCode = "unknown";
        try {
            JsonNode node = objectMapper.readTree(json);
            toolCode = node.path("tool").asText();
            ToolRequest tr = new ToolRequest();
            tr.setTraceId(request.getTraceId());
            tr.setUserId(request.getUserId());
            Map<String, Object> args = new HashMap<>();
            node.path("arguments").fields().forEachRemaining(e ->
                    args.put(e.getKey(), e.getValue().isValueNode() ? e.getValue().asText() : e.getValue()));
            tr.setArguments(args);
            ToolResponse resp = toolExecutor.execute(toolCode, tr);
            AgentRunResult.ToolEvent event = new AgentRunResult.ToolEvent(
                    toolCode, Boolean.TRUE.equals(resp.getSuccess()), resp.getErrorMessage());
            result.getToolEvents().add(event);
            if (onToolEvent != null) {
                onToolEvent.accept(event);
            }
            return objectMapper.writeValueAsString(Map.of(
                    "tool", toolCode,
                    "success", Boolean.TRUE.equals(resp.getSuccess()),
                    "data", resp.getData() == null ? "" : resp.getData(),
                    "error", resp.getErrorMessage() == null ? "" : resp.getErrorMessage()));
        } catch (Exception e) {
            AgentRunResult.ToolEvent event = new AgentRunResult.ToolEvent(toolCode, false, e.getMessage());
            result.getToolEvents().add(event);
            if (onToolEvent != null) {
                onToolEvent.accept(event);
            }
            return "{\"tool\":\"" + toolCode + "\",\"success\":false,\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    private List<LlmMessage> buildMessages(PersonaPack pack, AgentRunRequest request) {
        List<LlmMessage> messages = new ArrayList<>();
        String system = pack.getSystemPrompt();
        if (request.getExtraContext() != null && !request.getExtraContext().isBlank()) {
            system += "\n\n## 本次任务上下文\n" + request.getExtraContext();
        }
        messages.add(LlmMessage.system(system));
        if (request.getSessionId() != null) {
            sessionManager.getHistory(request.getSessionId(), 20).forEach(cm -> {
                if ("USER".equalsIgnoreCase(cm.getRole())) {
                    messages.add(LlmMessage.user(cm.getContent()));
                } else if ("ASSISTANT".equalsIgnoreCase(cm.getRole())) {
                    messages.add(LlmMessage.assistant(cm.getContent()));
                }
            });
        }
        messages.add(LlmMessage.user(request.getUserInput()));
        return messages;
    }
}
