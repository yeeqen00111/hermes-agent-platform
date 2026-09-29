package com.hermes.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hermes.agent.entity.AgentProfile;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent Profile Mapper
 */
@Mapper
public interface AgentProfileMapper extends BaseMapper<AgentProfile> {
}
