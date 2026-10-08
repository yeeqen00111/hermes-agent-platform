package com.hermes.agent.persona;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hermes.agent.entity.AgentProfile;
import com.hermes.agent.mapper.AgentProfileMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 新会话的版本绑定（ADR-009 / agent-platform §8.2 灰度）：
 * 未开灰度 → 当前发布版本；开了灰度 → 按 {@code grayRatio} 把**新会话**指向 {@code grayVersion}。
 * 版本只在建会话时定一次——**会话中途不切版本**（切了历史上下文与人设对不上）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionVersionResolver {

    private final AgentProfileMapper profileMapper;

    /** 解析新会话应绑定的版本号；Agent 不存在时返回 null（运行时按当前发布版本兜底） */
    public Integer resolve(String agentCode) {
        if (agentCode == null || agentCode.isBlank()) {
            return null;
        }
        AgentProfile profile = profileMapper.selectOne(new LambdaQueryWrapper<AgentProfile>()
                .eq(AgentProfile::getAgentCode, agentCode));
        if (profile == null) {
            return null;
        }
        Integer version = pick(profile.getCurrentVersion(), profile.getGrayVersion(),
                profile.getGrayRatio(), ThreadLocalRandom.current().nextInt(100));
        if (profile.getGrayVersion() != null && profile.getGrayRatio() != null && profile.getGrayRatio() > 0) {
            log.info("会话版本绑定: agentCode={} 发布={} 灰度={} 比例={}% -> 绑定={}",
                    agentCode, profile.getCurrentVersion(), profile.getGrayVersion(),
                    profile.getGrayRatio(), version);
        }
        return version;
    }

    /**
     * 纯函数便于测试：{@code roll ∈ [0,100)} 为抽样值。
     * 灰度未配置或比例 ≤0 → 发布版本；命中比例 → 灰度版本。
     */
    public static Integer pick(Integer currentVersion, Integer grayVersion, Integer grayRatio, int roll) {
        if (grayVersion == null || grayRatio == null || grayRatio <= 0) {
            return currentVersion;
        }
        return roll < Math.min(grayRatio, 100) ? grayVersion : currentVersion;
    }
}
