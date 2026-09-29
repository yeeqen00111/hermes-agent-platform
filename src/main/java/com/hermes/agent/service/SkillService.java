package com.hermes.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hermes.agent.entity.Skill;
import com.hermes.agent.entity.SkillVersion;
import com.hermes.agent.mapper.SkillMapper;
import com.hermes.agent.mapper.SkillVersionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 技能服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillService {

    private final SkillMapper skillMapper;
    private final SkillVersionMapper skillVersionMapper;

    /**
     * 根据编码查询技能
     */
    public Optional<Skill> getByCode(String skillCode) {
        LambdaQueryWrapper<Skill> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Skill::getSkillCode, skillCode);
        return Optional.ofNullable(skillMapper.selectOne(wrapper));
    }

    /**
     * 列出所有启用的技能
     */
    public List<Skill> listEnabled() {
        LambdaQueryWrapper<Skill> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Skill::getStatus, "ENABLED");
        return skillMapper.selectList(wrapper);
    }

    /**
     * 保存或更新技能
     */
    public void saveOrUpdate(Skill skill) {
        if (skill.getId() == null) {
            skillMapper.insert(skill);
            log.info("创建技能: {}", skill.getSkillCode());
        } else {
            skillMapper.updateById(skill);
            log.info("更新技能: {}", skill.getSkillCode());
        }
    }

    /**
     * 发布技能版本（创建快照）
     */
    public void publishVersion(String skillCode, String content, String requiresTools, String author) {
        Optional<Skill> skillOpt = getByCode(skillCode);
        if (skillOpt.isEmpty()) {
            log.warn("技能不存在: {}", skillCode);
            return;
        }

        Skill skill = skillOpt.get();
        int newVersion = skill.getCurrentVersion() + 1;

        // 创建版本快照
        SkillVersion version = new SkillVersion();
        version.setSkillCode(skillCode);
        version.setVersion(newVersion);
        version.setContent(content);
        version.setRequiresTools(requiresTools);
        version.setAuthor(author);
        version.setCreateTime(LocalDateTime.now());
        skillVersionMapper.insert(version);

        // 更新主表版本号
        skill.setCurrentVersion(newVersion);
        skillMapper.updateById(skill);

        log.info("发布技能版本: {} v{}", skillCode, newVersion);
    }

    /**
     * 获取技能的指定版本
     */
    public Optional<SkillVersion> getVersion(String skillCode, int version) {
        LambdaQueryWrapper<SkillVersion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SkillVersion::getSkillCode, skillCode)
               .eq(SkillVersion::getVersion, version);
        return Optional.ofNullable(skillVersionMapper.selectOne(wrapper));
    }

    /**
     * 获取技能的所有版本
     */
    public List<SkillVersion> listVersions(String skillCode) {
        LambdaQueryWrapper<SkillVersion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SkillVersion::getSkillCode, skillCode)
               .orderByDesc(SkillVersion::getVersion);
        return skillVersionMapper.selectList(wrapper);
    }
}
