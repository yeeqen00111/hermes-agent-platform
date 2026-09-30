package com.hermes.agent.persona;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hermes.agent.entity.AgentUserProfile;
import com.hermes.agent.mapper.AgentUserProfileMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 用户画像（USER身份层）：按 X-Actor 用户注入
 */
@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final AgentUserProfileMapper profileMapper;

    public AgentUserProfile get(Long userId) {
        if (userId == null) {
            return null;
        }
        return profileMapper.selectOne(new LambdaQueryWrapper<AgentUserProfile>()
                .eq(AgentUserProfile::getUserId, userId));
    }

    public AgentUserProfile save(AgentUserProfile profile) {
        AgentUserProfile existing = get(profile.getUserId());
        if (existing != null) {
            profile.setId(existing.getId());
            profileMapper.updateById(profile);
            return profile;
        }
        profileMapper.insert(profile);
        return profile;
    }
}
