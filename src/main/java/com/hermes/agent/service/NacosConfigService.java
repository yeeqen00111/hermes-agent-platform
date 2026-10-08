package com.hermes.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hermes.agent.entity.NacosCategory;
import com.hermes.agent.entity.NacosServer;
import com.hermes.agent.mapper.NacosCategoryMapper;
import com.hermes.agent.mapper.NacosServerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * nacos 服务器（白板·系统管理）：配置凭据（只存引用）+ 配置分类 / 服务分类（与系统挂钩）。
 * 数据面（nacos 配置/服务清单）后续经 nacos HTTP 接口或受控工具读取；本服务负责管理面与连通性探测。
 */
@Service
@RequiredArgsConstructor
public class NacosConfigService {

    private final NacosServerMapper serverMapper;
    private final NacosCategoryMapper categoryMapper;

    // ---------- 配置凭据（nacos 服务器） ----------

    public List<NacosServer> listServers() {
        List<NacosServer> list = serverMapper.selectList(new LambdaQueryWrapper<NacosServer>()
                .orderByAsc(NacosServer::getId));
        list.forEach(s -> s.setServerAddr(sanitize(s.getServerAddr())));
        return list;
    }

    public NacosServer saveServer(NacosServer server) {
        if (server.getId() != null) {
            // 未改凭据引用时保留原值（避免编辑表单回显空值覆盖）
            if (server.getSecretRef() == null || server.getSecretRef().isBlank()) {
                NacosServer old = serverMapper.selectById(server.getId());
                server.setSecretRef(old == null ? null : old.getSecretRef());
            }
            serverMapper.updateById(server);
        } else {
            serverMapper.insert(server);
        }
        server.setServerAddr(sanitize(server.getServerAddr()));
        return server;
    }

    public void deleteServer(Long id) {
        serverMapper.deleteById(id);
    }

    /**
     * 连通性探测：GET {addr}/nacos/v1/console/server/state（3s 超时）。
     */
    public Map<String, Object> testServer(String code) {
        NacosServer server = serverMapper.selectOne(new LambdaQueryWrapper<NacosServer>()
                .eq(NacosServer::getCode, code));
        if (server == null) {
            return Map.of("success", false, "message", "服务器不存在: " + code);
        }
        String addr = server.getServerAddr();
        if (addr == null || addr.isBlank()) {
            return Map.of("success", false, "message", "未配置服务地址");
        }
        String url = addr.replaceAll("/+$", "") + "/nacos/v1/console/server/state";
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(3)).build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(3)).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            boolean ok = response.statusCode() == 200;
            return Map.of("success", ok, "message", ok ? "连接正常" : ("HTTP " + response.statusCode()));
        } catch (Exception e) {
            return Map.of("success", false, "message", "连接失败: " + e.getMessage());
        }
    }

    // ---------- 配置分类 / 服务分类 ----------

    public List<NacosCategory> listCategories(String categoryType) {
        LambdaQueryWrapper<NacosCategory> wrapper = new LambdaQueryWrapper<NacosCategory>()
                .orderByAsc(NacosCategory::getId);
        if (categoryType != null && !categoryType.isBlank()) {
            wrapper.eq(NacosCategory::getCategoryType, categoryType);
        }
        return categoryMapper.selectList(wrapper);
    }

    public NacosCategory saveCategory(NacosCategory category) {
        if (category.getId() != null) {
            categoryMapper.updateById(category);
        } else {
            categoryMapper.insert(category);
        }
        return category;
    }

    public void deleteCategory(Long id) {
        categoryMapper.deleteById(id);
    }

    /** 脱敏：{@code ://user:token@} → {@code ://***@}（凭据不进库、也不出库） */
    private String sanitize(String url) {
        return url == null ? null : url.replaceAll("://[^/@]*@", "://***@");
    }
}
