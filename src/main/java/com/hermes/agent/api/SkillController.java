package com.hermes.agent.api;

import com.hermes.agent.entity.Skill;
import com.hermes.agent.entity.SkillVersion;
import com.hermes.agent.service.SkillService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 技能管理 API
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SkillController {

    private final SkillService skillService;

    /**
     * 列出所有技能
     */
    @GetMapping("/skills")
    public List<Skill> listSkills() {
        return skillService.listEnabled();
    }

    /**
     * 获取单个技能
     */
    @GetMapping("/skills/{code}")
    public Map<String, Object> getSkill(@PathVariable String code) {
        return skillService.getByCode(code)
                .map(skill -> Map.of("success", true, "data", skill))
                .orElse(Map.of("success", false, "message", "Skill not found"));
    }

    /**
     * 创建/更新技能
     */
    @PostMapping("/skills")
    public Map<String, Object> saveSkill(@RequestBody Skill skill) {
        skillService.saveOrUpdate(skill);
        return Map.of("success", true, "message", "Skill saved");
    }

    /**
     * 发布技能版本
     */
    @PostMapping("/skills/{code}/publish")
    public Map<String, Object> publishSkill(@PathVariable String code, @RequestBody PublishRequest request) {
        skillService.publishVersion(code, request.getContent(), request.getRequiresTools(), request.getAuthor());
        return Map.of("success", true, "message", "Skill version published");
    }

    /**
     * 获取技能版本历史
     */
    @GetMapping("/skills/{code}/versions")
    public List<SkillVersion> listVersions(@PathVariable String code) {
        return skillService.listVersions(code);
    }

    @Data
    public static class PublishRequest {
        private String content;
        private String requiresTools;
        private String author;
    }
}
