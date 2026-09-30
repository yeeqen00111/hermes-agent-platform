package com.hermes.agent.api;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hermes.agent.entity.AgentContextFile;
import com.hermes.agent.entity.AgentMemory;
import com.hermes.agent.entity.AgentProfile;
import com.hermes.agent.entity.AgentUserProfile;
import com.hermes.agent.mapper.AgentContextFileMapper;
import com.hermes.agent.persona.MemoryService;
import com.hermes.agent.persona.PersonaAssembler;
import com.hermes.agent.persona.PersonaPack;
import com.hermes.agent.persona.UserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 身份包管理：SOUL/AGENTS等上下文文件、记忆、用户画像的查看与维护
 */
@RestController
@RequestMapping("/api/personas")
@RequiredArgsConstructor
public class PersonaController {

    private final PersonaAssembler personaAssembler;
    private final AgentContextFileMapper contextFileMapper;
    private final MemoryService memoryService;
    private final UserProfileService userProfileService;

    /**
     * 身份包预览：组装后的系统提示与各身份层内容
     */
    @GetMapping("/{agentCode}/preview")
    public Map<String, Object> preview(@PathVariable String agentCode,
                                       @RequestParam(required = false) Long userId,
                                       @RequestParam(required = false) String sessionId) {
        PersonaPack pack = personaAssembler.assemble(agentCode, userId, sessionId);
        return Map.of(
                "agentCode", pack.getAgentCode(),
                "executionMode", pack.getProfile().getExecutionMode(),
                "triggerType", pack.getProfile().getTriggerType(),
                "soul", pack.getSoul() == null ? "" : pack.getSoul(),
                "agents", pack.getAgentsContext() == null ? "" : pack.getAgentsContext(),
                "memoryBlocks", pack.getMemoryBlocks(),
                "userProfile", pack.getUserProfile() == null ? "" : pack.getUserProfile(),
                "systemPrompt", pack.getSystemPrompt());
    }

    @GetMapping("/{agentCode}/context-files")
    public List<AgentContextFile> listContextFiles(@PathVariable String agentCode) {
        return contextFileMapper.selectList(new LambdaQueryWrapper<AgentContextFile>()
                .eq(AgentContextFile::getScope, "AGENT")
                .eq(AgentContextFile::getAgentCode, agentCode));
    }

    @PostMapping("/{agentCode}/context-files")
    public AgentContextFile saveContextFile(@PathVariable String agentCode,
                                            @RequestBody AgentContextFile file) {
        file.setScope("AGENT");
        file.setAgentCode(agentCode);
        if (file.getId() != null) {
            contextFileMapper.updateById(file);
        } else {
            contextFileMapper.insert(file);
        }
        return file;
    }

    @GetMapping("/{agentCode}/memories")
    public List<AgentMemory> listMemories(@PathVariable String agentCode,
                                          @RequestParam(required = false) String scope) {
        return memoryService.list(agentCode, scope);
    }

    @GetMapping("/user-profiles/{userId}")
    public AgentUserProfile getUserProfile(@PathVariable Long userId) {
        return userProfileService.get(userId);
    }

    @PostMapping("/user-profiles")
    public AgentUserProfile saveUserProfile(@RequestBody AgentUserProfile profile) {
        return userProfileService.save(profile);
    }

    @GetMapping("/{agentCode}/profile")
    public AgentProfile getProfile(@PathVariable String agentCode) {
        return personaAssembler.loadProfile(agentCode);
    }
}
