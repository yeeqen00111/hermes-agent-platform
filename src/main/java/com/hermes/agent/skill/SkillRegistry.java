package com.hermes.agent.skill;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hermes.agent.entity.Skill;
import com.hermes.agent.entity.SkillVersion;
import com.hermes.agent.mapper.SkillMapper;
import com.hermes.agent.mapper.SkillVersionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 技能注册表：启用中的技能元数据进内存索引（§6.4），正文按需从版本快照读取。
 * 索引以「启用技能数 + 版本号和」为失效标记，发布新版本后下一轮请求自动重建。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillRegistry {

    private final SkillMapper skillMapper;
    private final SkillVersionMapper skillVersionMapper;

    private volatile List<Skill> index = List.of();
    private volatile long stamp = -1;

    public synchronized void refresh() {
        List<Skill> skills = skillMapper.selectList(new LambdaQueryWrapper<Skill>()
                .eq(Skill::getStatus, "ENABLED"));
        index = List.copyOf(skills);
        stamp = skills.size() + skills.stream()
                .mapToLong(s -> s.getCurrentVersion() == null ? 0 : s.getCurrentVersion()).sum();
        log.info("技能索引已重建: {} 个启用技能", skills.size());
    }

    public List<Skill> enabledSkills() {
        ensureFresh();
        return index;
    }

    /**
     * 技能索引（§6.2 渐进披露）：每行 "code: description"，只进系统提示不进正文。
     */
    public String indexBlock() {
        StringBuilder sb = new StringBuilder();
        for (Skill s : enabledSkills()) {
            sb.append("- ").append(s.getSkillCode()).append(": ").append(s.getDescription()).append('\n');
        }
        return sb.toString();
    }

    /**
     * 按 Agent 启用清单过滤的索引；codes 为 null/空 表示全部可用（§8.1 enabledSkills 不配=全部可用）。
     */
    public String indexBlock(Collection<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return indexBlock();
        }
        StringBuilder sb = new StringBuilder();
        for (Skill s : enabledSkills()) {
            if (codes.contains(s.getSkillCode())) {
                sb.append("- ").append(s.getSkillCode()).append(": ").append(s.getDescription()).append('\n');
            }
        }
        return sb.toString();
    }

    /**
     * 读取技能当前版本全文（SKILL.md 正文）；技能未启用或无版本快照时为空。
     */
    public Optional<String> content(String skillCode) {
        Skill skill = enabledSkills().stream()
                .filter(s -> s.getSkillCode().equals(skillCode))
                .findFirst().orElse(null);
        if (skill == null) {
            return Optional.empty();
        }
        int version = skill.getCurrentVersion() == null ? 1 : skill.getCurrentVersion();
        SkillVersion snapshot = skillVersionMapper.selectOne(new LambdaQueryWrapper<SkillVersion>()
                .eq(SkillVersion::getSkillCode, skillCode)
                .eq(SkillVersion::getVersion, version));
        return Optional.ofNullable(snapshot).map(SkillVersion::getContent);
    }

    private void ensureFresh() {
        List<Skill> skills = skillMapper.selectList(new LambdaQueryWrapper<Skill>()
                .eq(Skill::getStatus, "ENABLED"));
        long current = skills.size() + skills.stream()
                .mapToLong(s -> s.getCurrentVersion() == null ? 0 : s.getCurrentVersion()).sum();
        if (current != stamp) {
            refresh();
        }
    }
}
