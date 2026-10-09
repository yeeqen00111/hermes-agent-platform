package com.hermes.agent.api;

import com.hermes.agent.service.PlatformConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 平台配置 API（白板·平台层）：平台日志 / 用量分析 / 数据看板。
 * （大模型配置见「Agent → 模型」`/api/admin/models`。）
 */
@RestController
@RequestMapping("/api/admin/platform")
@RequiredArgsConstructor
public class PlatformConfigController {

    private final PlatformConfigService service;

    @GetMapping("/logs")
    public List<Map<String, Object>> logs(@RequestParam(required = false) String level,
                                          @RequestParam(required = false) String keyword,
                                          @RequestParam(required = false, defaultValue = "200") int limit) {
        return service.logs(level, keyword, limit);
    }

    @GetMapping("/usage")
    public Map<String, Object> usage(@RequestParam(required = false, defaultValue = "7") int days) {
        return service.usage(days);
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        return service.dashboard();
    }
}
