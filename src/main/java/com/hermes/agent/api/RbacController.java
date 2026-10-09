package com.hermes.agent.api;

import com.hermes.agent.entity.SysMenu;
import com.hermes.agent.entity.SysRole;
import com.hermes.agent.service.SysRbacService;
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
 * 系统层 · 用户/角色/菜单 API（白板）。
 */
@RestController
@RequestMapping("/api/admin/rbac")
@RequiredArgsConstructor
public class RbacController {

    private final SysRbacService service;

    // ---------- 角色 ----------

    @GetMapping("/roles")
    public List<SysRole> listRoles() {
        return service.listRoles();
    }

    @PostMapping("/roles")
    public SysRole saveRole(@RequestBody SysRole role) {
        return service.saveRole(role);
    }

    @DeleteMapping("/roles/{id}")
    public Map<String, Object> deleteRole(@PathVariable Long id) {
        service.deleteRole(id);
        return Map.of("success", true);
    }

    // ---------- 菜单 ----------

    @GetMapping("/menus")
    public List<SysMenu> listMenus() {
        return service.listMenus();
    }

    @GetMapping("/menus/tree")
    public List<Map<String, Object>> menuTree() {
        return service.menuTree();
    }

    @PostMapping("/menus")
    public SysMenu saveMenu(@RequestBody SysMenu menu) {
        return service.saveMenu(menu);
    }

    @DeleteMapping("/menus/{id}")
    public Map<String, Object> deleteMenu(@PathVariable Long id) {
        return Map.of("success", true, "deleted", service.deleteMenu(id));
    }

    // ---------- 角色-菜单 ----------

    @GetMapping("/roles/{roleCode}/menus")
    public List<String> roleMenus(@PathVariable String roleCode) {
        return service.roleMenus(roleCode);
    }

    @PostMapping("/roles/{roleCode}/menus")
    public List<String> setRoleMenus(@PathVariable String roleCode, @RequestBody List<String> menuCodes) {
        return service.setRoleMenus(roleCode, menuCodes);
    }

    // ---------- 用户-角色 / 权限汇总 ----------

    @GetMapping("/users/{userId}/roles")
    public List<String> userRoles(@PathVariable Long userId) {
        return service.userRoles(userId);
    }

    @PostMapping("/users/{userId}/roles")
    public List<String> setUserRoles(@PathVariable Long userId, @RequestBody List<String> roleCodes) {
        return service.setUserRoles(userId, roleCodes);
    }

    @GetMapping("/users/{userId}/permissions")
    public Map<String, Object> permissions(@PathVariable Long userId) {
        return service.permissions(userId);
    }
}
