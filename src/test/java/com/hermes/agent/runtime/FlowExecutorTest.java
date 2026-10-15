package com.hermes.agent.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hermes.agent.dto.ToolRequest;
import com.hermes.agent.dto.ToolResponse;
import com.hermes.agent.entity.AgentProfile;
import com.hermes.agent.entity.FlowRun;
import com.hermes.agent.entity.FlowStepLog;
import com.hermes.agent.llm.LlmGateway;
import com.hermes.agent.mapper.FlowRunMapper;
import com.hermes.agent.mapper.FlowStepLogMapper;
import com.hermes.agent.persona.PersonaPack;
import com.hermes.agent.tool.ToolExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlowExecutorTest {

    private FlowRunMapper flowRunMapper;
    private FlowStepLogMapper stepLogMapper;
    private ToolExecutor toolExecutor;
    private LlmGateway llmGateway;
    private FlowExecutor flowExecutor;

    @BeforeEach
    void setUp() {
        flowRunMapper = mock(FlowRunMapper.class);
        stepLogMapper = mock(FlowStepLogMapper.class);
        toolExecutor = mock(ToolExecutor.class);
        llmGateway = mock(LlmGateway.class);
        flowExecutor = new FlowExecutor(flowRunMapper, stepLogMapper, toolExecutor,
                llmGateway, new ObjectMapper());
    }

    private PersonaPack flowPack(String flowDefinition) {
        AgentProfile profile = new AgentProfile();
        profile.setAgentCode("data-analyst");
        profile.setExecutionMode("FIXED_FLOW");
        profile.setFlowDefinition(flowDefinition);
        return PersonaPack.builder()
                .profile(profile)
                .systemPrompt("system")
                .build();
    }

    private AgentRunRequest request(String input) {
        return AgentRunRequest.builder()
                .agentCode("data-analyst")
                .userId(7L)
                .sessionId("s-flow")
                .userInput(input)
                .traceId("trace-flow")
                .build();
    }

    private ToolResponse okTool() {
        ToolResponse resp = new ToolResponse();
        resp.setSuccess(true);
        resp.setData(Map.of("total", 3));
        return resp;
    }

    @Test
    void argsFromUpstreamParsesUpstreamJsonIntoToolArguments() {
        String flow = "{\"steps\":["
                + "{\"code\":\"extract\",\"name\":\"抽取\",\"type\":\"LLM\",\"promptTemplate\":\"抽取参数 {{input}}\"},"
                + "{\"code\":\"query\",\"name\":\"查询\",\"type\":\"TOOL\",\"toolCode\":\"database.metric.query\",\"upstream\":\"extract\",\"argsFromUpstream\":true},"
                + "{\"code\":\"analyze\",\"name\":\"分析\",\"type\":\"LLM\",\"upstream\":\"query\",\"promptTemplate\":\"分析 {{prev}}\"}]}";
        when(llmGateway.chat(any(), anyList()))
                .thenReturn("{\"metric\":\"qps\",\"from\":\"2026-10-14 00:00:00\",\"to\":\"2026-10-15 00:00:00\"}")
                .thenReturn("结论：指标正常");
        when(toolExecutor.execute(eq("database.metric.query"), any())).thenReturn(okTool());

        List<String> deltas = new java.util.ArrayList<>();
        List<AgentRunResult.ToolEvent> events = new java.util.ArrayList<>();
        AgentRunResult result = flowExecutor.execute(flowPack(flow), request("查一下qps"),
                deltas::add, events::add, null);

        ArgumentCaptor<ToolRequest> captor = ArgumentCaptor.forClass(ToolRequest.class);
        verify(toolExecutor).execute(eq("database.metric.query"), captor.capture());
        Map<String, Object> args = captor.getValue().getArguments();
        assertThat(args).containsEntry("metric", "qps")
                .containsEntry("from", "2026-10-14 00:00:00")
                .containsEntry("to", "2026-10-15 00:00:00");
        assertThat(captor.getValue().getChannel()).isEqualTo("flow");
        assertThat(result.getReply()).isEqualTo("结论：指标正常");
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getToolCode()).isEqualTo("database.metric.query");
        assertThat(events.get(0).isSuccess()).isTrue();
        // 最后一步 LLM 输出走 delta 流式
        assertThat(deltas).isNotEmpty();
        assertThat(String.join("", deltas)).isEqualTo("结论：指标正常");
    }

    @Test
    void staticArgumentsArePassedThroughAndUpstreamJsonIgnoredWithoutFlag() {
        String flow = "{\"steps\":["
                + "{\"code\":\"aggregate\",\"name\":\"聚合\",\"type\":\"TOOL\",\"toolCode\":\"log.aggregate\","
                + "\"arguments\":{\"service\":\"svc-a\",\"from\":\"2026-10-14 00:00:00\",\"to\":\"2026-10-15 00:00:00\",\"groupBy\":\"level\"}},"
                + "{\"code\":\"summarize\",\"name\":\"摘要\",\"type\":\"LLM\",\"upstream\":\"aggregate\",\"promptTemplate\":\"摘要 {{prev}}\"}]}";
        when(toolExecutor.execute(eq("log.aggregate"), any())).thenReturn(okTool());
        when(llmGateway.chat(any(), anyList())).thenReturn("摘要内容");

        AgentRunResult result = flowExecutor.execute(flowPack(flow), request("随便输入"), null, null, null);

        ArgumentCaptor<ToolRequest> captor = ArgumentCaptor.forClass(ToolRequest.class);
        verify(toolExecutor).execute(eq("log.aggregate"), captor.capture());
        assertThat(captor.getValue().getArguments())
                .containsEntry("service", "svc-a")
                .containsEntry("groupBy", "level");
        assertThat(result.getReply()).isEqualTo("摘要内容");
    }

    @Test
    void nonJsonUpstreamFallsBackToInputArgument() {
        String flow = "{\"steps\":["
                + "{\"code\":\"extract\",\"name\":\"抽取\",\"type\":\"LLM\"},"
                + "{\"code\":\"query\",\"name\":\"查询\",\"type\":\"TOOL\",\"toolCode\":\"database.metric.query\",\"upstream\":\"extract\",\"argsFromUpstream\":true}]}";
        when(llmGateway.chat(any(), anyList())).thenReturn("（mock 回复）不是 JSON");
        when(toolExecutor.execute(eq("database.metric.query"), any())).thenReturn(okTool());

        flowExecutor.execute(flowPack(flow), request("问题"), null, null, null);

        ArgumentCaptor<ToolRequest> captor = ArgumentCaptor.forClass(ToolRequest.class);
        verify(toolExecutor).execute(eq("database.metric.query"), captor.capture());
        assertThat(captor.getValue().getArguments()).containsEntry("input", "（mock 回复）不是 JSON");
    }

    @Test
    void cancelBetweenStepsInterruptsAndMarksRunInterrupted() {
        String flow = "{\"steps\":["
                + "{\"code\":\"a\",\"name\":\"第一步\",\"type\":\"LLM\"},"
                + "{\"code\":\"b\",\"name\":\"第二步\",\"type\":\"LLM\",\"upstream\":\"a\"}]}";
        SessionCancellationRegistry registry = new SessionCancellationRegistry();
        SessionCancellationRegistry.Handle handle = registry.register("s-flow");
        when(llmGateway.chat(any(), anyList())).thenAnswer(inv -> {
            registry.cancel("s-flow");
            return "第一步输出";
        });

        AgentRunResult result = flowExecutor.execute(flowPack(flow), request("输入"), null, null, handle);

        assertThat(result.isInterrupted()).isTrue();
        assertThat(result.getReply()).isEqualTo(AgentRuntime.INTERRUPTED_REPLY);
        ArgumentCaptor<FlowRun> runCaptor = ArgumentCaptor.forClass(FlowRun.class);
        verify(flowRunMapper).updateById(runCaptor.capture());
        assertThat(runCaptor.getValue().getStatus()).isEqualTo("INTERRUPTED");
        verify(stepLogMapper, times(1)).insert(any(FlowStepLog.class));
    }

    @Test
    void toolFailureFailsRunWithHonestError() {
        String flow = "{\"steps\":[{\"code\":\"q\",\"name\":\"查询\",\"type\":\"TOOL\",\"toolCode\":\"database.metric.query\"}]}";
        ToolResponse fail = new ToolResponse();
        fail.setSuccess(false);
        fail.setErrorCode("METRIC_NOT_FOUND");
        fail.setErrorMessage("指标不存在");
        when(toolExecutor.execute(eq("database.metric.query"), any())).thenReturn(fail);

        AgentRunResult result = flowExecutor.execute(flowPack(flow), request("查询"), null, null, null);

        assertThat(result.getReply()).contains("流程执行失败").contains("METRIC_NOT_FOUND");
        assertThat(result.getToolEvents()).hasSize(1);
        assertThat(result.getToolEvents().get(0).isSuccess()).isFalse();
        ArgumentCaptor<FlowRun> runCaptor = ArgumentCaptor.forClass(FlowRun.class);
        verify(flowRunMapper).updateById(runCaptor.capture());
        assertThat(runCaptor.getValue().getStatus()).isEqualTo("FAILED");
    }
}
