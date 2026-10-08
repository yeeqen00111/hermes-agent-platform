package com.hermes.agent.persona;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hermes.agent.entity.AgentContextFile;
import com.hermes.agent.entity.AgentMemory;
import com.hermes.agent.entity.AgentProfile;
import com.hermes.agent.entity.AgentUserProfile;
import com.hermes.agent.entity.AiChatSession;
import com.hermes.agent.mapper.AgentContextFileMapper;
import com.hermes.agent.mapper.AgentProfileMapper;
import com.hermes.agent.mapper.AiChatSessionMapper;
import com.hermes.agent.skill.SkillRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 身份包组装器：把 SOUL/AGENTS/MEMORY/USER 四层身份配置装配为运行时 PersonaPack。
 * 同一基座上的不同功能智能体仅通过身份包区分。
 */
@Service
@RequiredArgsConstructor
public class PersonaAssembler {

    private final AgentProfileMapper profileMapper;
    private final AgentContextFileMapper contextFileMapper;
    private final AiChatSessionMapper chatSessionMapper;
    private final VersionService versionService;
    private final MemoryService memoryService;
    private final UserProfileService userProfileService;
    private final SkillRegistry skillRegistry;
    private final ObjectMapper objectMapper;

    public AgentProfile loadProfile(String agentCode) {
        String code = (agentCode == null || agentCode.isBlank()) ? "assistant" : agentCode;
        AgentProfile profile = profileMapper.selectOne(new LambdaQueryWrapper<AgentProfile>()
                .eq(AgentProfile::getAgentCode, code));
        if (profile == null) {
            profile = profileMapper.selectOne(new LambdaQueryWrapper<AgentProfile>()
                    .eq(AgentProfile::getAgentCode, "assistant"));
        }
        return profile;
    }

    public PersonaPack assemble(String agentCode, Long userId, String sessionId) {
        AgentProfile profile = loadProfile(agentCode);
        AiChatSession session = loadSession(sessionId);
        VersionService.Snapshot bound = boundSnapshot(profile, session);
        List<AgentContextFile> boundAgentFiles = null;
        if (bound != null && bound.profile() != null) {
            profile = bound.profile();
            boundAgentFiles = bound.contextFiles();
        }
        applySessionModelOverride(profile, session);
        String soul = contextContent(profile.getAgentCode(), "SOUL", boundAgentFiles);
        if (soul == null) {
            soul = profile.getSystemPrompt();
        }
        String agents = contextContent(profile.getAgentCode(), "AGENTS", boundAgentFiles);

        int topK = injectTopK(profile);
        List<AgentMemory> memories = memoryService.listForInjection(
                profile.getAgentCode(), userId, sessionId, topK);
        memories.forEach(m -> memoryService.touch(m.getId()));

        AgentUserProfile user = userProfileService.get(userId);

        PersonaPack pack = PersonaPack.builder()
                .profile(profile)
                .soul(soul)
                .agentsContext(agents)
                .skillIndex(skillRegistry.indexBlock(enabledSkillCodes(profile)))
                .memoryBlocks(memories.stream()
                        .map(m -> "- " + m.getMemoryKey() + ": " + m.getContent())
                        .collect(Collectors.toList()))
                .userProfile(user == null ? null : user.getProfileText())
                .build();
        pack.setSystemPrompt(renderSystemPrompt(pack));
        return pack;
    }

    private String renderSystemPrompt(PersonaPack pack) {
        StringBuilder sb = new StringBuilder();
        if (pack.getSoul() != null && !pack.getSoul().isBlank()) {
            sb.append(pack.getSoul().trim());
        } else {
            sb.append("你是HERMES智能体基座上的通用助手。");
        }
        if (pack.getAgentsContext() != null && !pack.getAgentsContext().isBlank()) {
            sb.append("\n\n## 环境约定\n").append(pack.getAgentsContext().trim());
        }
        if (pack.getSkillIndex() != null && !pack.getSkillIndex().isBlank()) {
            sb.append("\n\n## 可用技能\n需要时用 skill.load 工具按技能名加载全文。\n")
                    .append(pack.getSkillIndex().trim());
        }
        if (pack.getMemoryBlocks() != null && !pack.getMemoryBlocks().isEmpty()) {
            sb.append("\n\n## 记忆\n").append(String.join("\n", pack.getMemoryBlocks()));
        }
        if (pack.getUserProfile() != null && !pack.getUserProfile().isBlank()) {
            sb.append("\n\n## 当前用户\n").append(pack.getUserProfile().trim());
        }
        return sb.toString();
    }

    /**
     * 会话绑定的灰度版本：会话的 {@code agent_version} 与当前发布版本不一致时，
     * 说明该会话建会话时命中了灰度——按其快照组装（会话中途不切版本）。
     */
    private VersionService.Snapshot boundSnapshot(AgentProfile live, AiChatSession session) {
        if (live == null || session == null) {
            return null;
        }
        Integer boundVersion = session.getAgentVersion();
        if (boundVersion == null || boundVersion.equals(live.getCurrentVersion())) {
            return null;
        }
        VersionService.Snapshot snapshot = versionService.snapshot(live.getAgentCode(), boundVersion);
        if (snapshot == null) {
            return null;
        }
        return snapshot;
    }

    private AiChatSession loadSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }
        return chatSessionMapper.selectOne(new LambdaQueryWrapper<AiChatSession>()
                .eq(AiChatSession::getSessionId, sessionId));
    }

    /**
     * /model 指令的会话级覆盖：ai_chat_session.model_override（格式 provider/model），
     * 只影响本轮运行时选模，不改 Agent 档案。
     */
    private void applySessionModelOverride(AgentProfile profile, AiChatSession session) {
        String override = session == null ? null : session.getModelOverride();
        if (override == null || override.isBlank()) {
            return;
        }
        int slash = override.indexOf('/');
        if (slash > 0) {
            profile.setModelProvider(override.substring(0, slash));
            profile.setModelName(override.substring(slash + 1));
        } else {
            profile.setModelName(override);
        }
    }

    /**
     * 拼接某类身份文件内容。{@code boundAgentFiles != null} 时用版本快照里的 AGENT 级文件
     * （灰度会话），GLOBAL 级始终取当前库内值。
     */
    private String contextContent(String agentCode, String fileType, List<AgentContextFile> boundAgentFiles) {
        List<AgentContextFile> files = new ArrayList<>();
        if (boundAgentFiles == null) {
            files.addAll(contextFileMapper.selectList(new LambdaQueryWrapper<AgentContextFile>()
                    .eq(AgentContextFile::getFileType, fileType)
                    .eq(AgentContextFile::getScope, "AGENT")
                    .eq(AgentContextFile::getAgentCode, agentCode)));
        } else {
            boundAgentFiles.stream()
                    .filter(f -> fileType.equals(f.getFileType()) && "AGENT".equals(f.getScope()))
                    .forEach(files::add);
        }
        files.addAll(contextFileMapper.selectList(new LambdaQueryWrapper<AgentContextFile>()
                .eq(AgentContextFile::getFileType, fileType)
                .eq(AgentContextFile::getScope, "GLOBAL")));
        return files.stream().map(AgentContextFile::getContent).collect(Collectors.joining("\n"));
    }

    private int injectTopK(AgentProfile profile) {
        try {
            if (profile.getMemoryPolicy() != null && !profile.getMemoryPolicy().isBlank()) {
                JsonNode node = objectMapper.readTree(profile.getMemoryPolicy());
                return node.path("injectTopK").asInt(10);
            }
        } catch (Exception ignored) {
            // 策略解析失败按默认值
        }
        return 10;
    }

    /**
     * §8.1 enabledSkills 不配=全部可用；解析失败按全部可用（不因配置错误让技能整体消失）
     */
    private List<String> enabledSkillCodes(AgentProfile profile) {
        if (profile.getEnabledSkills() == null || profile.getEnabledSkills().isBlank()) {
            return List.of();
        }
        try {
            JsonNode node = objectMapper.readTree(profile.getEnabledSkills());
            List<String> codes = new ArrayList<>();
            if (node.isArray()) {
                node.forEach(n -> {
                    if (!n.isNull() && !n.asText().isBlank()) {
                        codes.add(n.asText());
                    }
                });
            }
            return codes;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    public boolean writebackEnabled(AgentProfile profile) {
        try {
            if (profile.getMemoryPolicy() != null && !profile.getMemoryPolicy().isBlank()) {
                return objectMapper.readTree(profile.getMemoryPolicy()).path("writeback").asBoolean(false);
            }
        } catch (Exception ignored) {
            // 策略解析失败按默认值
        }
        return false;
    }
}
