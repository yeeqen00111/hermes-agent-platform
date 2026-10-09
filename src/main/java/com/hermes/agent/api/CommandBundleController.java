package com.hermes.agent.api;

import com.hermes.agent.entity.AiCommandBundle;
import com.hermes.agent.service.CommandBundleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 指令（捆绑包）管理 API（白板·Agent 层·补充项「指令」）：
 * 一条指令预载一串技能；对话中以 /{bundleCode} 生效。
 */
@RestController
@RequestMapping("/api/admin/command-bundles")
@RequiredArgsConstructor
public class CommandBundleController {

    private final CommandBundleService service;

    @GetMapping
    public List<AiCommandBundle> list() {
        return service.list();
    }

    @PostMapping
    public AiCommandBundle save(@RequestBody AiCommandBundle bundle) {
        return service.save(bundle);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        service.delete(id);
        return Map.of("success", true);
    }

    /** 拼装预览：该指令命中时会预载的技能正文 */
    @GetMapping("/{id}/preview")
    public Map<String, Object> preview(@PathVariable Long id) {
        return service.preview(id);
    }
}
