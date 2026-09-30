package com.hermes.agent.persona;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hermes.agent.entity.AgentMemory;
import com.hermes.agent.mapper.AgentMemoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 记忆存储：AGENT/USER/SESSION 三层，按策略注入与写回
 */
@Service
@RequiredArgsConstructor
public class MemoryService {

    private final AgentMemoryMapper memoryMapper;

    public List<AgentMemory> listForInjection(String agentCode, Long userId, String sessionId, int topK) {
        if (topK <= 0) {
            return List.of();
        }
        LambdaQueryWrapper<AgentMemory> q = new LambdaQueryWrapper<AgentMemory>()
                .eq(AgentMemory::getAgentCode, agentCode)
                .and(w -> w
                        .and(a -> a.eq(AgentMemory::getScope, "AGENT").eq(AgentMemory::getScopeKey, ""))
                        .or(u -> u.eq(AgentMemory::getScope, "USER").eq(AgentMemory::getScopeKey, String.valueOf(userId)))
                        .or(s -> s.eq(AgentMemory::getScope, "SESSION").eq(AgentMemory::getScopeKey, sessionId)))
                .orderByDesc(AgentMemory::getLastHitTime)
                .orderByDesc(AgentMemory::getId)
                .last("LIMIT " + topK);
        return memoryMapper.selectList(q);
    }

    public AgentMemory writeback(String agentCode, String scope, String scopeKey, String key, String content) {
        AgentMemory existing = memoryMapper.selectOne(new LambdaQueryWrapper<AgentMemory>()
                .eq(AgentMemory::getAgentCode, agentCode)
                .eq(AgentMemory::getScope, scope)
                .eq(AgentMemory::getScopeKey, scopeKey == null ? "" : scopeKey)
                .eq(AgentMemory::getMemoryKey, key));
        if (existing != null) {
            existing.setContent(content);
            existing.setSource("LLM_WRITEBACK");
            memoryMapper.updateById(existing);
            return existing;
        }
        AgentMemory m = new AgentMemory();
        m.setAgentCode(agentCode);
        m.setScope(scope);
        m.setScopeKey(scopeKey == null ? "" : scopeKey);
        m.setMemoryKey(key);
        m.setContent(content);
        m.setSource("LLM_WRITEBACK");
        m.setHitCount(0);
        memoryMapper.insert(m);
        return m;
    }

    public void touch(Long memoryId) {
        AgentMemory m = memoryMapper.selectById(memoryId);
        if (m == null) {
            return;
        }
        m.setHitCount((m.getHitCount() == null ? 0 : m.getHitCount()) + 1);
        m.setLastHitTime(LocalDateTime.now());
        memoryMapper.updateById(m);
    }

    public List<AgentMemory> list(String agentCode, String scope) {
        return memoryMapper.selectList(new LambdaQueryWrapper<AgentMemory>()
                .eq(AgentMemory::getAgentCode, agentCode)
                .eq(scope != null, AgentMemory::getScope, scope));
    }
}
