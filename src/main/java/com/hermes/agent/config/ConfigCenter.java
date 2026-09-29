package com.hermes.agent.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 配置版本中心（热更新载体）
 */
@Slf4j
@Component
public class ConfigCenter {

    private final Map<String, ConfigVersion> versions = new ConcurrentHashMap<>();

    public ConfigCenter() {
        // 初始化各scope的版本号
        initVersions();
    }

    private void initVersions() {
        String[] scopes = {"agent", "skill", "mcp", "model", "command", "channel"};
        for (String scope : scopes) {
            versions.put(scope, new ConfigVersion(scope, 1, LocalDateTime.now()));
        }
    }

    /**
     * 获取指定scope的版本号
     */
    public int getVersion(String scope) {
        ConfigVersion version = versions.get(scope);
        return version != null ? version.getVersion() : 0;
    }

    /**
     * 递增版本号（配置变更时调用）
     */
    public int incrementVersion(String scope) {
        ConfigVersion version = versions.computeIfPresent(scope,
                (k, v) -> new ConfigVersion(k, v.getVersion() + 1, LocalDateTime.now()));

        if (version == null) {
            version = new ConfigVersion(scope, 1, LocalDateTime.now());
            versions.put(scope, version);
        }

        log.info("配置版本递增: {} -> {}", scope, version.getVersion());
        return version.getVersion();
    }

    /**
     * 检查版本是否变化
     */
    public boolean hasChanged(String scope, int lastKnownVersion) {
        int currentVersion = getVersion(scope);
        return currentVersion != lastKnownVersion;
    }

    @Data
    public static class ConfigVersion {
        private String scope;
        private int version;
        private LocalDateTime updateTime;

        public ConfigVersion(String scope, int version, LocalDateTime updateTime) {
            this.scope = scope;
            this.version = version;
            this.updateTime = updateTime;
        }
    }
}
