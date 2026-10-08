package com.hermes.agent.monitor;

import com.hermes.agent.entity.AgentProfile;
import com.hermes.agent.llm.LlmGateway;
import com.hermes.agent.llm.LlmMessage;
import com.hermes.agent.notify.NotificationGateway;
import com.hermes.agent.persona.PersonaAssembler;
import com.hermes.agent.persona.PersonaPack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HalfDayReportServiceTest {

    private DashboardQueryService queryService;
    private PersonaAssembler personaAssembler;
    private LlmGateway llmGateway;
    private NotificationGateway gateway;
    private HalfDayReportService service;

    @BeforeEach
    void setUp() {
        queryService = mock(DashboardQueryService.class);
        personaAssembler = mock(PersonaAssembler.class);
        llmGateway = mock(LlmGateway.class);
        gateway = mock(NotificationGateway.class);
        service = new HalfDayReportService(queryService, personaAssembler, llmGateway, gateway);
        ReflectionTestUtils.setField(service, "defaultWindowHours", 12);
        ReflectionTestUtils.setField(service, "alertChannelType", "FEISHU");
        ReflectionTestUtils.setField(service, "alertRecipient", "ops@example.com");
    }

    private Map<String, Object> stats(int runTotal) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("windowHours", 12);
        m.put("runTotal", runTotal);
        return m;
    }

    private PersonaPack pack(String provider, String model, String systemPrompt) {
        AgentProfile p = new AgentProfile();
        p.setModelProvider(provider);
        p.setModelName(model);
        return PersonaPack.builder().profile(p).systemPrompt(systemPrompt).build();
    }

    @Test
    void reportUsesIdentityPackageForModelAndSystemPrompt() {
        when(queryService.overview(anyInt())).thenReturn(stats(3));
        when(queryService.recentFailures(anyInt(), anyInt())).thenReturn(List.of());
        when(queryService.orderViolations(anyInt())).thenReturn(List.of());
        when(personaAssembler.assemble(eq(HalfDayReportService.REPORT_AGENT_CODE), any(), any()))
                .thenReturn(pack("identity-provider", "identity-model", "身份包系统提示"));
        when(llmGateway.chat(any(), any())).thenReturn("BODY-MARKDOWN");

        service.generateAndSend(12);

        ArgumentCaptor<AgentProfile> profileCaptor = ArgumentCaptor.forClass(AgentProfile.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LlmMessage>> msgCaptor = ArgumentCaptor.forClass(List.class);
        verify(llmGateway).chat(profileCaptor.capture(), msgCaptor.capture());

        assertThat(profileCaptor.getValue().getModelProvider()).isEqualTo("identity-provider");
        assertThat(profileCaptor.getValue().getModelName()).isEqualTo("identity-model");
        assertThat(msgCaptor.getValue().get(0).getContent()).isEqualTo("身份包系统提示");
        verify(gateway).sendByType(eq("FEISHU"), eq("ops@example.com"),
                eq("[HERMES] 半天流程报表"), contains("BODY-MARKDOWN"));
    }

    @Test
    void noRunsSkipsGenerationAndSending() {
        when(queryService.overview(anyInt())).thenReturn(stats(0));
        when(queryService.recentFailures(anyInt(), anyInt())).thenReturn(List.of());

        service.generateAndSend(12);

        verify(personaAssembler, never()).assemble(any(), any(), any());
        verify(llmGateway, never()).chat(any(), any());
        verify(gateway, never()).sendByType(any(), any(), any(), any());
    }
}
