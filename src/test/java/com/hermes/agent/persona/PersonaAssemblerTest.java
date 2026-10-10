package com.hermes.agent.persona;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hermes.agent.entity.AgentContextFile;
import com.hermes.agent.entity.AiChatSession;
import com.hermes.agent.mapper.AgentContextFileMapper;
import com.hermes.agent.mapper.AgentProfileMapper;
import com.hermes.agent.mapper.AgentVersionMapper;
import com.hermes.agent.mapper.AiChatSessionMapper;
import com.hermes.agent.plugin.PluginRegistry;
import com.hermes.agent.skill.SkillRegistry;
import com.hermes.agent.service.OpsEvolutionService;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 灰度会话按绑定版本组装人格包：会话 agent_version 指向旧版本时，
 * SOUL/AGENTS 取该版本快照，而不是当前发布版本（ADR-009 / §8.2）。
 */
class PersonaAssemblerTest {

    private SingleConnectionDataSource dataSource;
    private SqlSession sqlSession;
    private AgentProfileMapper profileMapper;
    private AgentContextFileMapper contextFileMapper;
    private AiChatSessionMapper sessionMapper;
    private VersionService versionService;
    private PersonaAssembler assembler;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        new ResourceDatabasePopulator(new ClassPathResource("db/schema-sqlite.sql")).execute(dataSource);
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setLocalCacheScope(LocalCacheScope.STATEMENT);
        configuration.addMapper(AgentProfileMapper.class);
        configuration.addMapper(AgentContextFileMapper.class);
        configuration.addMapper(AgentVersionMapper.class);
        configuration.addMapper(AiChatSessionMapper.class);
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(configuration);
        sqlSession = factory.getObject().openSession(true);

        profileMapper = sqlSession.getMapper(AgentProfileMapper.class);
        contextFileMapper = sqlSession.getMapper(AgentContextFileMapper.class);
        sessionMapper = sqlSession.getMapper(AiChatSessionMapper.class);
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        versionService = new VersionService(profileMapper, contextFileMapper,
                sqlSession.getMapper(AgentVersionMapper.class), objectMapper);

        MemoryService memoryService = mock(MemoryService.class);
        when(memoryService.listForInjection(any(), any(), any(), anyInt())).thenReturn(List.of());
        UserProfileService userProfileService = mock(UserProfileService.class);
        when(userProfileService.get(any())).thenReturn(null);
        SkillRegistry skillRegistry = mock(SkillRegistry.class);
        when(skillRegistry.indexBlock(any())).thenReturn("");
        OpsEvolutionService opsEvolutionService = mock(OpsEvolutionService.class);
        when(opsEvolutionService.experienceBlocks(any(), anyInt())).thenReturn(List.of());
        PluginRegistry pluginRegistry = mock(PluginRegistry.class);
        when(pluginRegistry.promptBlocks(any())).thenReturn(List.of());
        when(pluginRegistry.skillCodesForAgent(any())).thenReturn(java.util.Set.of());

        assembler = new PersonaAssembler(profileMapper, contextFileMapper, sessionMapper, versionService,
                memoryService, userProfileService, skillRegistry, objectMapper, opsEvolutionService, pluginRegistry);
    }

    @AfterEach
    void tearDown() {
        if (sqlSession != null) {
            sqlSession.close();
        }
        if (dataSource != null) {
            dataSource.destroy();
        }
    }

    private AgentContextFile assistantSoul() {
        return contextFileMapper.selectList(null).stream()
                .filter(f -> "assistant".equals(f.getAgentCode()))
                .filter(f -> "SOUL".equals(f.getFileType()))
                .findFirst().orElseThrow();
    }

    private void updateSoul(String content) {
        AgentContextFile soul = assistantSoul();
        soul.setContent(content);
        contextFileMapper.updateById(soul);
    }

    private void bindSession(String sessionId, Integer agentVersion) {
        AiChatSession row = new AiChatSession();
        row.setSessionId(sessionId);
        row.setUserId(7L);
        row.setAgentCode("assistant");
        row.setAgentVersion(agentVersion);
        row.setStatus("ACTIVE");
        sessionMapper.insert(row);
    }

    @Test
    void graySessionAssemblesBoundVersionPersona() {
        updateSoul("OLD-SOUL");
        versionService.publish("assistant", "tester");   // v1 = OLD-SOUL
        updateSoul("NEW-SOUL");
        versionService.publish("assistant", "tester");   // v2 = NEW-SOUL（当前发布）

        bindSession("s-gray", 1);
        bindSession("s-live", 2);

        String graySoul = assembler.assemble("assistant", 7L, "s-gray").getSoul();
        String liveSoul = assembler.assemble("assistant", 7L, "s-live").getSoul();

        assertThat(graySoul).contains("OLD-SOUL").doesNotContain("NEW-SOUL");
        assertThat(liveSoul).contains("NEW-SOUL").doesNotContain("OLD-SOUL");
    }
}
