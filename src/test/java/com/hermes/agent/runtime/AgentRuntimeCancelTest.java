package com.hermes.agent.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hermes.agent.dto.ToolResponse;
import com.hermes.agent.entity.AgentProfile;
import com.hermes.agent.llm.LlmGateway;
import com.hermes.agent.persona.PersonaAssembler;
import com.hermes.agent.persona.PersonaPack;
import com.hermes.agent.session.SessionManager;
import com.hermes.agent.tool.ToolExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentRuntimeCancelTest {

    private static final String SESSION = "session-cancel";

    private PersonaAssembler personaAssembler;
    private LlmGateway llmGateway;
    private ToolExecutor toolExecutor;
    private FlowExecutor flowExecutor;
    private SessionManager sessionManager;
    private SessionCancellationRegistry registry;
    private AgentRuntime runtime;

    @BeforeEach
    void setUp() {
        personaAssembler = mock(PersonaAssembler.class);
        llmGateway = mock(LlmGateway.class);
        toolExecutor = mock(ToolExecutor.class);
        flowExecutor = mock(FlowExecutor.class);
        sessionManager = mock(SessionManager.class);
        registry = new SessionCancellationRegistry();
        runtime = new AgentRuntime(personaAssembler, llmGateway, toolExecutor, flowExecutor,
                sessionManager, registry, new ObjectMapper());
    }

    /** 真实身份包：isFixedFlow/getAgentCode 是手写方法，避免 mock 真实体纠缠 */
    private PersonaPack llmDrivenPack() {
        AgentProfile profile = new AgentProfile();
        profile.setAgentCode("assistant");
        profile.setExecutionMode("LLM_DRIVEN");
        return PersonaPack.builder()
                .profile(profile)
                .systemPrompt("system prompt")
                .build();
    }

    private AgentRunRequest request() {
        return AgentRunRequest.builder()
                .agentCode("assistant")
                .userId(7L)
                .sessionId(SESSION)
                .userInput("你好")
                .traceId("trace-1")
                .build();
    }

    @Test
    void normalRunReturnsModelReplyWithoutInterruptFlag() {
        when(personaAssembler.assemble(any(), any(), any())).thenReturn(llmDrivenPack());
        when(sessionManager.getHistory(any(), anyInt())).thenReturn(List.of());
        when(llmGateway.chat(any(), anyList())).thenReturn("final plain answer");

        AgentRunResult result = runtime.run(request());

        assertThat(result.isInterrupted()).isFalse();
        assertThat(result.getReply()).isEqualTo("final plain answer");
        verify(toolExecutor, never()).execute(any(), any(), any());
        assertThat(registry.runningHandle(SESSION)).isEmpty();
    }

    @Test
    void cancelIssuedDuringLlmCallStopsTheRoundImmediately() {
        when(personaAssembler.assemble(any(), any(), any())).thenReturn(llmDrivenPack());
        when(sessionManager.getHistory(any(), anyInt())).thenReturn(List.of());
        // 模拟用户在生成期间点停止：LLM 调用返回前同进程置取消位（阻塞调用本身不可打断，ADR-012）
        when(llmGateway.chat(any(), anyList())).thenAnswer(inv -> {
            registry.cancel(SESSION);
            return "answer that must be discarded";
        });

        AgentRunResult result = runtime.run(request());

        assertThat(result.isInterrupted()).isTrue();
        assertThat(result.getReply()).isEqualTo(AgentRuntime.INTERRUPTED_REPLY);
        verify(toolExecutor, never()).execute(any(), any(), any());
        assertThat(registry.runningHandle(SESSION)).isEmpty();
    }

    @Test
    void cancelIssuedDuringToolExecutionStopsBeforeNextRound() {
        when(personaAssembler.assemble(any(), any(), any())).thenReturn(llmDrivenPack());
        when(sessionManager.getHistory(any(), anyInt())).thenReturn(List.of());
        when(llmGateway.chat(any(), anyList()))
                .thenReturn("```tool_call\n{\"tool\":\"x\",\"arguments\":{}}\n```")
                .thenReturn("must not be reached");
        ToolResponse toolResp = mock(ToolResponse.class);
        when(toolExecutor.execute(any(), any(), any())).thenAnswer(inv -> {
            registry.cancel(SESSION);
            return toolResp;
        });

        AgentRunResult result = runtime.run(request());

        assertThat(result.isInterrupted()).isTrue();
        assertThat(result.getToolEvents()).hasSize(1);
        verify(llmGateway, times(1)).chat(any(), anyList());
        verify(toolExecutor, times(1)).execute(any(), any(), any());
        assertThat(registry.runningHandle(SESSION)).isEmpty();
    }
}

