package com.hermes.agent.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置
 */
@Configuration
public class MybatisPlusConfig {

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    /**
     * 分页插件（按数据源自动选方言：SQLite 开发 / MySQL 生产）
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        DbType dbType = datasourceUrl != null && datasourceUrl.startsWith("jdbc:mysql")
                ? DbType.MYSQL : DbType.SQLITE;
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(dbType));
        return interceptor;
    }
}
