package com.hermes.agent.persona;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hermes.agent.entity.AgentContextFile;
import com.hermes.agent.entity.AgentProfile;
import com.hermes.agent.entity.AgentVersion;
import com.hermes.agent.mapper.AgentContextFileMapper;
import com.hermes.agent.mapper.AgentProfileMapper;
import com.hermes.agent.mapper.AgentVersionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 人格包版本化：发布=写不可变快照+移指针，回滚=移指针回旧快照（ADR-009）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VersionService {

    private final AgentProfileMapper profileMapper;
    private final AgentContextFileMapper contextFileMapper;
    private final AgentVersionMapper versionMapper;
    private final ObjectMapper objectMapper;

    public List<AgentVersion> versions(String agentCode) {
        return versionMapper.selectList(new LambdaQueryWrapper<AgentVersion>()
                .eq(AgentVersion::getAgentCode, agentCode)
                .orderByDesc(AgentVersion::getVersion));
    }

    @Transactional
    public AgentVersion publish(String agentCode, String by) {
        AgentProfile profile = requireProfile(agentCode);
        List<AgentContextFile> files = listAgentFiles(agentCode);

        AgentVersion snapshotRow = new AgentVersion();
        snapshotRow.setAgentCode(agentCode);
        snapshotRow.setVersion(nextVersion(agentCode));
        snapshotRow.setSnapshot(writeSnapshot(profile, files));
        snapshotRow.setPublishTime(LocalDateTime.now());
        snapshotRow.setPublishBy(by);
        versionMapper.insert(snapshotRow);

        profile.setStatus("PUBLISHED");
        profile.setCurrentVersion(snapshotRow.getVersion());
        profileMapper.updateById(profile);
        log.info("persona published: agentCode={} version={}", agentCode, snapshotRow.getVersion());
        return snapshotRow;
    }

    @Transactional
    public AgentProfile rollback(String agentCode, int version) {
        AgentVersion row = versionMapper.selectOne(new LambdaQueryWrapper<AgentVersion>()
                .eq(AgentVersion::getAgentCode, agentCode)
                .eq(AgentVersion::getVersion, version));
        if (row == null) {
            throw new IllegalArgumentException("版本不存在: " + agentCode + "@" + version);
        }
        AgentProfile current = requireProfile(agentCode);
        try {
            JsonNode root = objectMapper.readTree(row.getSnapshot());
            AgentProfile snap = objectMapper.treeToValue(root.get("profile"), AgentProfile.class);
            snap.setId(current.getId());
            snap.setStatus("PUBLISHED");
            snap.setCurrentVersion(version);
            profileMapper.updateById(snap);

            contextFileMapper.delete(new LambdaQueryWrapper<AgentContextFile>()
                    .eq(AgentContextFile::getScope, "AGENT")
                    .eq(AgentContextFile::getAgentCode, agentCode));
            for (JsonNode node : root.get("contextFiles")) {
                AgentContextFile file = objectMapper.treeToValue(node, AgentContextFile.class);
                file.setId(null);
                contextFileMapper.insert(file);
            }
        } catch (Exception e) {
            throw new IllegalStateException("快照解析失败: " + e.getMessage(), e);
        }
        log.info("persona rolled back: agentCode={} -> version={}", agentCode, version);
        return profileMapper.selectById(current.getId());
    }

    private AgentProfile requireProfile(String agentCode) {
        AgentProfile profile = profileMapper.selectOne(new LambdaQueryWrapper<AgentProfile>()
                .eq(AgentProfile::getAgentCode, agentCode));
        if (profile == null) {
            throw new IllegalArgumentException("Agent 不存在: " + agentCode);
        }
        return profile;
    }

    private List<AgentContextFile> listAgentFiles(String agentCode) {
        return contextFileMapper.selectList(new LambdaQueryWrapper<AgentContextFile>()
                .eq(AgentContextFile::getScope, "AGENT")
                .eq(AgentContextFile::getAgentCode, agentCode));
    }

    private Integer nextVersion(String agentCode) {
        List<AgentVersion> existing = versions(agentCode);
        return existing.isEmpty() ? 1 : existing.get(0).getVersion() + 1;
    }

    private String writeSnapshot(AgentProfile profile, List<AgentContextFile> files) {
        try {
            return objectMapper.writeValueAsString(Map.of("profile", profile, "contextFiles", files));
        } catch (Exception e) {
            throw new IllegalStateException("快照序列化失败: " + e.getMessage(), e);
        }
    }
}
