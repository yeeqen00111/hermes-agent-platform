package com.hermes.agent.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.hermes.agent.entity.SysMenu;
import com.hermes.agent.entity.SysRole;
import com.hermes.agent.entity.SysUser;
import com.hermes.agent.mapper.SysMenuMapper;
import com.hermes.agent.mapper.SysRoleMapper;
import com.hermes.agent.mapper.SysRoleMenuMapper;
import com.hermes.agent.mapper.SysUserMapper;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 系统层 RBAC 测试：角色/菜单 CRUD、角色-菜单授权、用户-角色、权限汇总、菜单级联删除。
 */
class SysRbacServiceTest {

    private SingleConnectionDataSource dataSource;
    private SqlSession sqlSession;
    private SysRbacService service;
    private SysMenuMapper menuMapper;
    private SysUserMapper userMapper;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        new ResourceDatabasePopulator(new ClassPathResource("db/schema-sqlite.sql")).execute(dataSource);
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setLocalCacheScope(LocalCacheScope.STATEMENT);
        configuration.addMapper(SysRoleMapper.class);
        configuration.addMapper(SysMenuMapper.class);
        configuration.addMapper(SysRoleMenuMapper.class);
        configuration.addMapper(SysUserMapper.class);
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(configuration);
        sqlSession = factory.getObject().openSession(true);

        menuMapper = sqlSession.getMapper(SysMenuMapper.class);
        userMapper = sqlSession.getMapper(SysUserMapper.class);
        service = new SysRbacService(sqlSession.getMapper(SysRoleMapper.class), menuMapper,
                sqlSession.getMapper(SysRoleMenuMapper.class), userMapper);
    }

    @AfterEach
    void tearDown() {
        if (sqlSession != null) {
            sqlSession.close();
        }
        if (dataSource != null) {
            dataSource.destroy();
        }
    }

    private SysRole role(String code) {
        SysRole r = new SysRole();
        r.setCode(code);
        r.setName(code);
        return service.saveRole(r);
    }

    private SysMenu menu(String code, Long parentId) {
        SysMenu m = new SysMenu();
        m.setCode(code);
        m.setName(code);
        m.setParentId(parentId);
        m.setSortNo(1);
        return service.saveMenu(m);
    }

    private Long user(String code) {
        SysUser u = new SysUser();
        u.setUserCode(code);
        u.setName(code);
        u.setStatus("ACTIVE");
        userMapper.insert(u);
        return u.getId();
    }

    @Test
    void saveRoleAppliesDefaultStatus() {
        SysRole saved = role("ops-admin");
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
        assertThat(service.listRoles()).hasSize(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void menuTreeNestsChildren() {
        SysMenu root = menu("sys", 0L);
        menu("sys.users", root.getId());

        List<Map<String, Object>> tree = service.menuTree();

        assertThat(tree).hasSize(1);
        assertThat((List<Map<String, Object>>) tree.get(0).get("children")).hasSize(1);
    }

    @Test
    void roleMenusBindingReplaces() {
        role("ops");
        service.setRoleMenus("ops", List.of("sys", "sys.users"));
        assertThat(service.roleMenus("ops")).containsExactlyInAnyOrder("sys", "sys.users");

        service.setRoleMenus("ops", List.of("sys"));
        assertThat(service.roleMenus("ops")).containsExactly("sys");
    }

    @Test
    @SuppressWarnings("unchecked")
    void permissionsAggregateFromUserRoles() {
        SysMenu sys = menu("sys", 0L);
        menu("sys.users", sys.getId());
        menu("sys.audit", sys.getId());
        role("ops");
        role("auditor");
        service.setRoleMenus("ops", List.of("sys", "sys.users"));
        service.setRoleMenus("auditor", List.of("sys.audit"));
        Long uid = user("u1");

        service.setUserRoles(uid, List.of("ops", "auditor"));
        Map<String, Object> perms = service.permissions(uid);

        assertThat((List<String>) perms.get("roleCodes")).containsExactly("ops", "auditor");
        assertThat((List<String>) perms.get("menuCodes")).containsExactlyInAnyOrder("sys", "sys.users", "sys.audit");
        assertThat((List<SysMenu>) perms.get("menus")).hasSize(3);
    }

    @Test
    void setUserRolesRoundTrip() {
        role("ops");
        Long uid = user("u2");
        assertThat(service.userRoles(uid)).isEmpty();
        service.setUserRoles(uid, List.of("ops"));
        assertThat(service.userRoles(uid)).containsExactly("ops");
    }

    @Test
    void deleteMenuCascadesChildrenAndBindings() {
        SysMenu root = menu("sys", 0L);
        menu("sys.users", root.getId());
        role("ops");
        service.setRoleMenus("ops", List.of("sys", "sys.users"));

        int deleted = service.deleteMenu(root.getId());

        assertThat(deleted).isEqualTo(2);
        assertThat(menuMapper.selectCount(new LambdaQueryWrapper<>())).isZero();
        assertThat(service.roleMenus("ops")).isEmpty();
    }

    @Test
    void deleteRoleRemovesBindings() {
        SysRole r = role("ops");
        service.setRoleMenus("ops", List.of("sys"));
        service.deleteRole(r.getId());
        assertThat(service.listRoles()).isEmpty();
        assertThat(service.roleMenus("ops")).isEmpty();
    }

    @Test
    void permissionsOfUserWithoutRolesIsEmpty() {
        Long uid = user("u3");
        Map<String, Object> perms = service.permissions(uid);
        assertThat((List<?>) perms.get("roleCodes")).isEmpty();
        assertThat((List<?>) perms.get("menuCodes")).isEmpty();
    }
}
