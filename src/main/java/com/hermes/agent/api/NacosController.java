package com.hermes.agent.api;

import com.hermes.agent.entity.NacosCategory;
import com.hermes.agent.entity.NacosServer;
import com.hermes.agent.service.NacosConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * nacos 服务器 API（白板·系统管理）：配置凭据 + 配置分类 / 服务分类。
 */
@RestController
@RequestMapping("/api/admin/nacos")
@RequiredArgsConstructor
public class NacosController {

    private final NacosConfigService service;

    // ---------- 配置凭据 ----------

    @GetMapping("/servers")
    public List<NacosServer> listServers() {
        return service.listServers();
    }

    @PostMapping("/servers")
    public NacosServer saveServer(@RequestBody NacosServer server) {
        return service.saveServer(server);
    }

    @DeleteMapping("/servers/{id}")
    public Map<String, Object> deleteServer(@PathVariable Long id) {
        service.deleteServer(id);
        return Map.of("success", true);
    }

    @PostMapping("/servers/{code}/test")
    public Map<String, Object> testServer(@PathVariable String code) {
        return service.testServer(code);
    }

    // ---------- 配置分类 / 服务分类 ----------

    @GetMapping("/categories")
    public List<NacosCategory> listCategories(@RequestParam(required = false) String type) {
        return service.listCategories(type);
    }

    @PostMapping("/categories")
    public NacosCategory saveCategory(@RequestBody NacosCategory category) {
        return service.saveCategory(category);
    }

    @DeleteMapping("/categories/{id}")
    public Map<String, Object> deleteCategory(@PathVariable Long id) {
        service.deleteCategory(id);
        return Map.of("success", true);
    }
}
