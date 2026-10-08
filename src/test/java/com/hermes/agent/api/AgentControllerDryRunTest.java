package com.hermes.agent.api;

import com.hermes.agent.entity.AgentProfile;
import com.hermes.agent.persona.VersionService;
import com.hermes.agent.runtime.AgentRunRequest;
import com.hermes.agent.runtime.AgentRunResult;
import com.hermes.agent.runtime.AgentRuntime;
import com.hermes.agent.service.AgentProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentControllerDryRunTest {

    private AgentProfileService profileService;
    private VersionService versionService;
    private AgentRuntime agentRuntime;
    private AgentController controller;

    @BeforeEach
    void setUp() {
        profileService = mock(AgentProfileService.class);
        versionService = mock(VersionService.class);
        agentRuntime = mock(AgentRuntime.class);
        controller = new AgentController(profileService, versionService, agentRuntime);
    }

    private AgentProfile profile() {
        AgentProfile p = new AgentProfile();
        p.setId(1L);
        p.setAgentCode("assistant");
        p.setExecutionMode("LLM_DRIVEN");
        return p;
    }

    private AgentController.DryRunRequest request(String message) {
        AgentController.DryRunRequest r = new AgentController.DryRunRequest();
        r.setMessage(message);
        return r;
    }

    @Test
    void runsDraftWithoutSessionAndTagsAuditChannel() {
        when(profileService.getById(1L)).thenReturn(Optional.of(profile()));
        when(agentRuntime.run(any())).thenReturn(AgentRunResult.builder().reply("草稿试跑回复").build());

        Map<String, Object> resp = controller.dryRunAgent(1L, request("你好"));

        assertThat(resp.get("success")).isEqualTo(true);
        assertThat(resp.get("agentCode")).isEqualTo("assistant");
        assertThat(resp.get("executionMode")).isEqualTo("LLM_DRIVEN");
        assertThat(resp.get("reply")).isEqualTo("草稿试跑回复");

        ArgumentCaptor<AgentRunRequest> captor = ArgumentCaptor.forClass(AgentRunRequest.class);
        verify(agentRuntime).run(captor.capture());
        AgentRunRequest run = captor.getValue();
        assertThat(run.getAgentCode()).isEqualTo("assistant");
        assertThat(run.getUserInput()).isEqualTo("你好");
        assertThat(run.getChannel()).as("试跑须与线上对话区分").isEqualTo("dry-run");
        assertThat(run.getSessionId()).as("试跑不建会话、不落历史").isNull();
        assertThat(run.getTraceId()).isNotBlank();
    }

    @Test
    void rejectsBlankMessageWithoutRunning() {
        Map<String, Object> resp = controller.dryRunAgent(1L, request("   "));

        assertThat(resp.get("success")).isEqualTo(false);
        verify(agentRuntime, never()).run(any());
    }

    @Test
    void unknownAgentReturnsFailure() {
        when(profileService.getById(9L)).thenReturn(Optional.empty());

        Map<String, Object> resp = controller.dryRunAgent(9L, request("你好"));

        assertThat(resp.get("success")).isEqualTo(false);
        verify(agentRuntime, never()).run(any());
    }
}
