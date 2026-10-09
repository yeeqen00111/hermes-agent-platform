package com.hermes.agent.api;

import com.hermes.agent.entity.SysTenant;
import com.hermes.agent.service.TenantService;
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
 * 系统层 · 租户管理 API（白板）。
 */
@RestController
@RequestMapping("/api/admin/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService service;

    @GetMapping
    public List<SysTenant> list() {
        return service.list();
    }

    @PostMapping
    public SysTenant save(@RequestBody SysTenant tenant) {
        return service.save(tenant);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        service.delete(id);
        return Map.of("success", true);
    }
}
