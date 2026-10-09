package com.hermes.agent.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.hermes.agent.entity.OpsGrant;
import com.hermes.agent.mapper.OpsGrantMapper;
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
 * 智能运维授权测试：按助手/类型过滤、可见范围汇总、X-Data-Scope 导出、停用排除。
 */
class OpsGrantServiceTest {

    private SingleConnectionDataSource dataSource;
    private SqlSession sqlSession;
    private OpsGrantService service;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        new ResourceDatabasePopulator(new ClassPathResource("db/schema-sqlite.sql")).execute(dataSource);
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setLocalCacheScope(LocalCacheScope.STATEMENT);
        configuration.addMapper(OpsGrantMapper.class);
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(configuration);
        sqlSession = factory.getObject().openSession(true);
        service = new OpsGrantService(sqlSession.getMapper(OpsGrantMapper.class));
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

    private OpsGrant grant(String code, String agent, String type, String ref, int enabled) {
        OpsGrant g = new OpsGrant();
        g.setCode(code);
        g.setName(code);
        g.setAgentCode(agent);
        g.setGrantType(type);
        g.setResourceRef(ref);
        g.setEnabled(enabled);
        return service.saveGrant(g);
    }

    @Test
    void listFiltersByAgentAndType() {
        grant("g1", "assistant", "REPO", "repo-a", 1);
        grant("g2", "assistant", "LOG", "kafka-app", 1);
        grant("g3", "other", "REPO", "repo-b", 1);

        assertThat(service.listGrants("assistant", null)).hasSize(2);
        assertThat(service.listGrants("assistant", "repo")).extracting(OpsGrant::getCode).containsExactly("g1");
    }

    @Test
    @SuppressWarnings("unchecked")
    void scopeGroupsByType() {
        grant("g1", "assistant", "REPO", "repo-a", 1);
        grant("g2", "assistant", "REPO", "repo-b", 1);
        grant("g3", "assistant", "NACOS", "nacos-prod", 1);
        grant("g4", "assistant", "LOG", "订单系统", 1);

        Map<String, Object> scope = service.scopeOf("assistant");

        assertThat((List<String>) scope.get("repo")).containsExactly("repo-a", "repo-b");
        assertThat((List<String>) scope.get("nacos")).containsExactly("nacos-prod");
        assertThat((List<String>) scope.get("log")).containsExactly("订单系统");
    }

    @Test
    @SuppressWarnings("unchecked")
    void disabledGrantsExcludedFromScope() {
        grant("g1", "assistant", "REPO", "repo-a", 1);
        grant("g2", "assistant", "REPO", "repo-off", 0);

        assertThat((List<String>) service.scopeOf("assistant").get("repo")).containsExactly("repo-a");
    }

    @Test
    void asDataScopeExportsDimensions() {
        grant("g1", "assistant", "REPO", "repo-a", 1);
        grant("g2", "assistant", "LOG", "订单系统", 1);

        Map<String, List<String>> scope = service.asDataScope("assistant");

        assertThat(scope).containsKeys("repo", "log");
        assertThat(scope.get("repo")).containsExactly("repo-a");
    }

    @Test
    void saveDefaultsPermissionAndNormalizesType() {
        OpsGrant g = new OpsGrant();
        g.setCode("g1");
        g.setName("g1");
        g.setAgentCode("assistant");
        g.setGrantType("repo");
        g.setResourceRef("repo-a");
        OpsGrant saved = service.saveGrant(g);

        assertThat(saved.getGrantType()).isEqualTo("REPO");
        assertThat(saved.getPermission()).isEqualTo("READ");
        assertThat(saved.getEnabled()).isEqualTo(1);
    }
}
