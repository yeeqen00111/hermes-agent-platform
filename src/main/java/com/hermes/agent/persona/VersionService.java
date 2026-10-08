package com.hermes.agent.persona;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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

    /**
     * 开/关灰度：{@code ratio<=0} 表示关闭（清空目标版本）。
     * 开启时必须指定一个已发布的版本号，否则拒绝（灰度不能指向未发布快照）。
     */
    @Transactional
    public AgentProfile setGray(String agentCode, Integer version, int ratio) {
        AgentProfile profile = requireProfile(agentCode);
        if (ratio <= 0) {
            // updateById 会忽略 null，必须用 UpdateWrapper 显式清空 gray_version
            profileMapper.update(null, new LambdaUpdateWrapper<AgentProfile>()
                    .eq(AgentProfile::getId, profile.getId())
                    .set(AgentProfile::getGrayVersion, null)
                    .set(AgentProfile::getGrayRatio, 0));
            profile.setGrayVersion(null);
            profile.setGrayRatio(0);
            log.info("persona gray off: agentCode={}", agentCode);
            return profile;
        }
        if (version == null) {
            throw new IllegalArgumentException("开启灰度必须指定目标版本");
        }
        AgentVersion row = versionMapper.selectOne(new LambdaQueryWrapper<AgentVersion>()
                .eq(AgentVersion::getAgentCode, agentCode)
                .eq(AgentVersion::getVersion, version));
        if (row == null) {
            throw new IllegalArgumentException("版本不存在: " + agentCode + "@" + version);
        }
        profile.setGrayVersion(version);
        profile.setGrayRatio(Math.min(ratio, 100));
        profileMapper.updateById(profile);
        log.info("persona gray on: agentCode={} -> version={} ratio={}%", agentCode, version, profile.getGrayRatio());
        return profile;
    }

    /**
     * 读取指定版本的不可变快照（发布时的人格 + AGENT 级上下文文件）。
     * 供灰度会话按其绑定版本组装人格包；版本不存在或快照损坏返回 null。
     */
    public Snapshot snapshot(String agentCode, int version) {
        AgentVersion row = versionMapper.selectOne(new LambdaQueryWrapper<AgentVersion>()
                .eq(AgentVersion::getAgentCode, agentCode)
                .eq(AgentVersion::getVersion, version));
        if (row == null) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(row.getSnapshot());
            AgentProfile profile = objectMapper.treeToValue(root.get("profile"), AgentProfile.class);
            List<AgentContextFile> files = new java.util.ArrayList<>();
            for (JsonNode node : root.path("contextFiles")) {
                files.add(objectMapper.treeToValue(node, AgentContextFile.class));
            }
            return new Snapshot(profile, files);
        } catch (Exception e) {
            log.warn("快照解析失败: agentCode={} version={} err={}", agentCode, version, e.getMessage());
            return null;
        }
    }

    /** 版本快照：人格 + 该 Agent 的上下文文件（NORTH 快照语义，GLOBAL 文件不入快照） */
    public record Snapshot(AgentProfile profile, List<AgentContextFile> contextFiles) {
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
