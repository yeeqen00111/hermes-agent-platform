package com.hermes.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hermes.agent.entity.SysMenu;
import com.hermes.agent.entity.SysRole;
import com.hermes.agent.entity.SysRoleMenu;
import com.hermes.agent.entity.SysUser;
import com.hermes.agent.mapper.SysMenuMapper;
import com.hermes.agent.mapper.SysRoleMapper;
import com.hermes.agent.mapper.SysRoleMenuMapper;
import com.hermes.agent.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 系统层 · 用户/角色/菜单（白板）：
 * 角色维护、菜单树维护、角色-菜单授权；用户→角色沿用 {@code sys_user.role_codes}（逗号分隔），
 * 并可按用户汇总其菜单权限。
 */
@Service
@RequiredArgsConstructor
public class SysRbacService {

    private final SysRoleMapper roleMapper;
    private final SysMenuMapper menuMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysUserMapper userMapper;

    // ---------- 角色 ----------

    public List<SysRole> listRoles() {
        return roleMapper.selectList(new LambdaQueryWrapper<SysRole>().orderByAsc(SysRole::getId));
    }

    public SysRole saveRole(SysRole role) {
        if (role.getStatus() == null || role.getStatus().isBlank()) {
            role.setStatus("ACTIVE");
        }
        if (role.getId() != null) {
            roleMapper.updateById(role);
        } else {
            roleMapper.insert(role);
        }
        return role;
    }

    @Transactional
    public void deleteRole(Long id) {
        SysRole role = roleMapper.selectById(id);
        if (role != null) {
            roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>()
                    .eq(SysRoleMenu::getRoleCode, role.getCode()));
        }
        roleMapper.deleteById(id);
    }

    // ---------- 菜单 ----------

    public List<SysMenu> listMenus() {
        return menuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                .orderByAsc(SysMenu::getSortNo).orderByAsc(SysMenu::getId));
    }

    public SysMenu saveMenu(SysMenu menu) {
        if (menu.getParentId() == null) {
            menu.setParentId(0L);
        }
        if (menu.getSortNo() == null) {
            menu.setSortNo(0);
        }
        if (menu.getStatus() == null || menu.getStatus().isBlank()) {
            menu.setStatus("ACTIVE");
        }
        if (menu.getId() != null) {
            menuMapper.updateById(menu);
        } else {
            menuMapper.insert(menu);
        }
        return menu;
    }

    /** 删除菜单及其子孙，并清理角色绑定 */
    @Transactional
    public int deleteMenu(Long id) {
        List<Long> ids = new ArrayList<>();
        ids.add(id);
        collectMenuDescendants(id, ids);
        List<String> codes = menuMapper.selectBatchIds(ids).stream().map(SysMenu::getCode).toList();
        if (!codes.isEmpty()) {
            roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().in(SysRoleMenu::getMenuCode, codes));
        }
        return menuMapper.deleteBatchIds(ids);
    }

    private void collectMenuDescendants(Long parentId, List<Long> acc) {
        List<SysMenu> children = menuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getParentId, parentId));
        for (SysMenu c : children) {
            acc.add(c.getId());
            collectMenuDescendants(c.getId(), acc);
        }
    }

    /** 菜单树（管理面展示） */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> menuTree() {
        List<SysMenu> all = listMenus();
        Map<Long, Map<String, Object>> byId = new LinkedHashMap<>();
        for (SysMenu m : all) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", m.getId());
            node.put("code", m.getCode());
            node.put("name", m.getName());
            node.put("parentId", m.getParentId());
            node.put("path", m.getPath());
            node.put("sortNo", m.getSortNo());
            node.put("children", new ArrayList<Map<String, Object>>());
            byId.put(m.getId(), node);
        }
        List<Map<String, Object>> roots = new ArrayList<>();
        for (SysMenu m : all) {
            Map<String, Object> node = byId.get(m.getId());
            Long pid = m.getParentId();
            if (pid == null || pid == 0 || !byId.containsKey(pid)) {
                roots.add(node);
            } else {
                ((List<Map<String, Object>>) byId.get(pid).get("children")).add(node);
            }
        }
        return roots;
    }

    // ---------- 角色-菜单授权 ----------

    public List<String> roleMenus(String roleCode) {
        return roleMenuMapper.selectList(new LambdaQueryWrapper<SysRoleMenu>()
                        .eq(SysRoleMenu::getRoleCode, roleCode))
                .stream().map(SysRoleMenu::getMenuCode).toList();
    }

    @Transactional
    public List<String> setRoleMenus(String roleCode, List<String> menuCodes) {
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleCode, roleCode));
        if (menuCodes != null) {
            for (String code : menuCodes) {
                if (code == null || code.isBlank()) {
                    continue;
                }
                SysRoleMenu rm = new SysRoleMenu();
                rm.setRoleCode(roleCode);
                rm.setMenuCode(code);
                roleMenuMapper.insert(rm);
            }
        }
        return roleMenus(roleCode);
    }

    // ---------- 用户-角色 ----------

    public List<String> userRoles(Long userId) {
        SysUser user = userMapper.selectById(userId);
        return user == null ? List.of() : splitCodes(user.getRoleCodes());
    }

    public List<String> setUserRoles(Long userId, List<String> roleCodes) {
        SysUser patch = new SysUser();
        patch.setId(userId);
        patch.setRoleCodes(roleCodes == null ? "" : String.join(",", roleCodes));
        userMapper.updateById(patch);
        return userRoles(userId);
    }

    /**
     * 按用户汇总权限：其角色编码 + 角色菜单并集 + 菜单明细。
     */
    public Map<String, Object> permissions(Long userId) {
        List<String> roleCodes = userRoles(userId);
        Set<String> menuCodes = new LinkedHashSet<>();
        List<SysMenu> menus = List.of();
        if (!roleCodes.isEmpty()) {
            roleMenuMapper.selectList(new LambdaQueryWrapper<SysRoleMenu>()
                            .in(SysRoleMenu::getRoleCode, roleCodes))
                    .forEach(rm -> menuCodes.add(rm.getMenuCode()));
            if (!menuCodes.isEmpty()) {
                menus = menuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                        .in(SysMenu::getCode, menuCodes)
                        .orderByAsc(SysMenu::getSortNo));
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("userId", userId);
        out.put("roleCodes", roleCodes);
        out.put("menuCodes", new ArrayList<>(menuCodes));
        out.put("menus", menus);
        return out;
    }

    private static List<String> splitCodes(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();
    }
}
