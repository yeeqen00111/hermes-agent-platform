package com.hermes.agent.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.hermes.agent.entity.NacosCategory;
import com.hermes.agent.entity.NacosServer;
import com.hermes.agent.mapper.NacosCategoryMapper;
import com.hermes.agent.mapper.NacosServerMapper;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * nacos 服务器（白板·系统管理）服务测试：跑真 SQLite，验证凭据脱敏、分类按类型过滤、连通性探测失败路径。
 */
class NacosConfigServiceTest {

    private SingleConnectionDataSource dataSource;
    private SqlSession sqlSession;
    private NacosConfigService service;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        new ResourceDatabasePopulator(new ClassPathResource("db/schema-sqlite.sql")).execute(dataSource);
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setLocalCacheScope(LocalCacheScope.STATEMENT);
        configuration.addMapper(NacosServerMapper.class);
        configuration.addMapper(NacosCategoryMapper.class);
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(configuration);
        sqlSession = factory.getObject().openSession(true);
        service = new NacosConfigService(sqlSession.getMapper(NacosServerMapper.class),
                sqlSession.getMapper(NacosCategoryMapper.class));
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

    private void saveServer(String code, String addr) {
        NacosServer s = new NacosServer();
        s.setCode(code);
        s.setName(code);
        s.setServerAddr(addr);
        s.setUsername("nacos");
        s.setSecretRef("NACOS_" + code.toUpperCase() + "_SECRET");
        s.setEnabled(1);
        service.saveServer(s);
    }

    @Test
    void saveAndListServers() {
        saveServer("prod", "http://nacos-prod:8848");
        assertThat(service.listServers()).extracting(NacosServer::getCode).containsExactly("prod");
    }

    @Test
    void serverAddrIsSanitizedOnOutput() {
        saveServer("leaky", "http://user:token@nacos:8848");
        assertThat(service.listServers().get(0).getServerAddr()).isEqualTo("http://***@nacos:8848");
    }

    @Test
    void editWithoutSecretRefKeepsOriginalRef() {
        saveServer("prod", "http://nacos-prod:8848");
        NacosServer edit = new NacosServer();
        edit.setId(service.listServers().get(0).getId());
        edit.setCode("prod");
        edit.setName("prod-renamed");
        edit.setServerAddr("http://nacos-prod:8848");
        edit.setEnabled(1);
        service.saveServer(edit);
        NacosServer stored = service.listServers().get(0);
        assertThat(stored.getName()).isEqualTo("prod-renamed");
        assertThat(stored.getSecretRef()).isEqualTo("NACOS_PROD_SECRET");
    }

    @Test
    void unknownServerTestFails() {
        assertThat(service.testServer("nope")).containsEntry("success", false);
    }

    @Test
    void serverWithoutAddrTestFails() {
        saveServer("empty", null);
        assertThat(service.testServer("empty")).containsEntry("success", false);
    }

    @Test
    void categoriesFilterByType() {
        NacosCategory cfg = new NacosCategory();
        cfg.setCode("order-config");
        cfg.setName("订单配置");
        cfg.setCategoryType("CONFIG");
        cfg.setServerCode("prod");
        cfg.setSystemName("订单系统");
        service.saveCategory(cfg);

        NacosCategory svc = new NacosCategory();
        svc.setCode("order-service");
        svc.setName("订单服务");
        svc.setCategoryType("SERVICE");
        svc.setServerCode("prod");
        svc.setSystemName("订单系统");
        service.saveCategory(svc);

        assertThat(service.listCategories("CONFIG")).extracting(NacosCategory::getCode)
                .containsExactly("order-config");
        assertThat(service.listCategories("SERVICE")).extracting(NacosCategory::getCode)
                .containsExactly("order-service");
        assertThat(service.listCategories(null)).hasSize(2);
    }
}
