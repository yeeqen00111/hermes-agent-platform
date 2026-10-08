package com.hermes.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hermes.agent.entity.AgentProfile;
import com.hermes.agent.mapper.AgentProfileMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Agent Profile 服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentProfileService {

    private final AgentProfileMapper agentProfileMapper;

    /**
     * 根据编码查询Agent
     */
    public Optional<AgentProfile> getByCode(String agentCode) {
        LambdaQueryWrapper<AgentProfile> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentProfile::getAgentCode, agentCode);
        return Optional.ofNullable(agentProfileMapper.selectOne(wrapper));
    }

    /**
     * 根据主键查询Agent
     */
    public Optional<AgentProfile> getById(Long id) {
        return Optional.ofNullable(agentProfileMapper.selectById(id));
    }

    /**
     * 列出所有启用的Agent
     */
    public List<AgentProfile> listEnabled() {
        LambdaQueryWrapper<AgentProfile> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentProfile::getStatus, "PUBLISHED");
        return agentProfileMapper.selectList(wrapper);
    }

    /**
     * 保存或更新Agent
     */
    public void saveOrUpdate(AgentProfile profile) {
        if (profile.getId() == null) {
            agentProfileMapper.insert(profile);
            log.info("创建Agent: {}", profile.getAgentCode());
        } else {
            agentProfileMapper.updateById(profile);
            log.info("更新Agent: {}", profile.getAgentCode());
        }
    }
}
