package com.hermes.agent.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.hermes.agent.entity.SysTenant;
import com.hermes.agent.mapper.SysTenantMapper;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 系统层租户管理测试。
 */
class TenantServiceTest {

    private SingleConnectionDataSource dataSource;
    private SqlSession sqlSession;
    private TenantService service;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        new ResourceDatabasePopulator(new ClassPathResource("db/schema-sqlite.sql")).execute(dataSource);
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setLocalCacheScope(LocalCacheScope.STATEMENT);
        configuration.addMapper(SysTenantMapper.class);
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(configuration);
        sqlSession = factory.getObject().openSession(true);
        service = new TenantService(sqlSession.getMapper(SysTenantMapper.class));
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

    @Test
    void saveAppliesDefaultStatusAndLists() {
        SysTenant t = new SysTenant();
        t.setCode("acme");
        t.setName("ACME 集团");
        SysTenant saved = service.save(t);

        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
        assertThat(service.list()).extracting(SysTenant::getCode).containsExactly("acme");
    }

    @Test
    void updateAndDelete() {
        SysTenant t = new SysTenant();
        t.setCode("acme");
        t.setName("ACME");
        SysTenant saved = service.save(t);

        saved.setName("ACME 更名");
        service.save(saved);
        assertThat(service.list().get(0).getName()).isEqualTo("ACME 更名");

        service.delete(saved.getId());
        assertThat(service.list()).isEmpty();
    }
}
