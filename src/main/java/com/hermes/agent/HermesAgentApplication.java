package com.hermes.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.jdbc.JdbcRepositoriesAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.nio.file.Files;
import java.nio.file.Paths;

// 持久层只用MyBatis-Plus；Spring Data JDBC不识别SQLite方言，排除其自动配置
@SpringBootApplication(exclude = JdbcRepositoriesAutoConfiguration.class)
@EnableScheduling
public class HermesAgentApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(HermesAgentApplication.class);
        app.addInitializers(ctx -> {
            String url = ctx.getEnvironment().getProperty("spring.datasource.url", "");
            if (url.startsWith("jdbc:sqlite:")) {
                try {
                    var parent = Paths.get(url.substring("jdbc:sqlite:".length()))
                            .toAbsolutePath().getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                } catch (Exception e) {
                    throw new IllegalStateException("无法创建SQLite数据目录", e);
                }
            }
        });
        app.run(args);
    }
}
