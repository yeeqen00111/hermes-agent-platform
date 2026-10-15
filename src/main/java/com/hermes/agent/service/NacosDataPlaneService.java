package com.hermes.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hermes.agent.entity.NacosServer;
import com.hermes.agent.mapper.NacosServerMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Nacos 数据面（白板·智能运维，本平台自研）：配置快照 / 变更历史 / 服务实例。
 * 服务器与凭据来自 ai_nacos_server（secretRef 存环境变量名，库中与日志中无明文）。
 * 鉴权：配置了 username 时先 /nacos/v1/auth/login 取 accessToken。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NacosDataPlaneService {

    private final NacosServerMapper serverMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 配置快照：GET /nacos/v1/cs/configs */
    public Map<String, Object> configQuery(String environment, String namespace,
                                           String group, String dataId) {
        return call(environment, server -> {
            Map<String, String> params = baseParams(server, namespace);
            params.put("dataId", dataId);
            params.put("group", group);
            HttpResponse<String> resp = get(server, "/nacos/v1/cs/configs", params);
            if (resp.statusCode() == 404) {
                return Map.of("success", false, "errorCode", "CONFIG_NOT_FOUND",
                        "message", "配置不存在: " + group + " / " + dataId);
            }
            if (resp.statusCode() != 200) {
                return httpError(resp);
            }
            Map<String, Object> out = ok();
            out.put("dataId", dataId);
            out.put("group", group);
            out.put("content", resp.body());
            return out;
        });
    }

    /** 变更历史：GET /nacos/v1/cs/history?search=accurate */
    public Map<String, Object> changeQuery(String environment, String namespace,
                                           String group, String dataId, Integer limit) {
        return call(environment, server -> {
            Map<String, String> params = baseParams(server, namespace);
            params.put("search", "accurate");
            if (dataId != null && !dataId.isBlank()) {
                params.put("dataId", dataId);
            }
            if (group != null && !group.isBlank()) {
                params.put("group", group);
            }
            params.put("pageNo", "1");
            params.put("pageSize", String.valueOf(limit == null || limit < 1 ? 20 : Math.min(limit, 100)));
            HttpResponse<String> resp = get(server, "/nacos/v1/cs/history", params);
            if (resp.statusCode() != 200) {
                return httpError(resp);
            }
            Map<String, Object> out = ok();
            out.put("history", objectMapper.readTree(resp.body()));
            return out;
        });
    }

    /** 服务实例：GET /nacos/v1/ns/instance/list */
    public Map<String, Object> instanceQuery(String environment, String namespace,
                                             String serviceName, String cluster) {
        return call(environment, server -> {
            Map<String, String> params = baseParams(server, namespace);
            params.put("serviceName", serviceName);
            if (cluster != null && !cluster.isBlank()) {
                params.put("clusterName", cluster);
            }
            HttpResponse<String> resp = get(server, "/nacos/v1/ns/instance/list", params);
            if (resp.statusCode() != 200) {
                return httpError(resp);
            }
            JsonNode node = objectMapper.readTree(resp.body());
            Map<String, Object> out = ok();
            out.put("serviceName", serviceName);
            out.put("hosts", node.path("hosts"));
            return out;
        });
    }

    // ---------- 内部 ----------

    private interface Call {
        Map<String, Object> run(NacosServer server) throws Exception;
    }

    private Map<String, Object> call(String environment, Call fn) {
        NacosServer server = resolveServer(environment);
        if (server == null) {
            return Map.of("success", false, "errorCode", "NACOS_NOT_CONFIGURED",
                    "message", environment == null || environment.isBlank()
                            ? "未配置可用的 Nacos 服务器（先到「Nacos 服务器」维护并启用）"
                            : "未找到编码为 " + environment + " 且启用的 Nacos 服务器");
        }
        try {
            return fn.run(server);
        } catch (Exception e) {
            log.warn("Nacos 数据面调用失败: server={}, {}", server.getCode(), e.getMessage());
            return Map.of("success", false, "errorCode", "NACOS_CALL_FAILED",
                    "message", "Nacos 调用失败: " + e.getMessage());
        }
    }

    private NacosServer resolveServer(String environment) {
        LambdaQueryWrapper<NacosServer> wrapper = new LambdaQueryWrapper<NacosServer>()
                .eq(NacosServer::getEnabled, 1);
        if (environment != null && !environment.isBlank()) {
            wrapper.eq(NacosServer::getCode, environment);
        }
        wrapper.orderByAsc(NacosServer::getId).last("LIMIT 1");
        return serverMapper.selectOne(wrapper);
    }

    private Map<String, String> baseParams(NacosServer server, String namespace) {
        Map<String, String> params = new LinkedHashMap<>();
        String ns = namespace == null || namespace.isBlank() ? server.getNamespaceId() : namespace;
        if (ns != null && !ns.isBlank()) {
            params.put("tenant", ns);
        }
        return params;
    }

    private HttpResponse<String> get(NacosServer server, String path, Map<String, String> params)
            throws Exception {
        StringBuilder url = new StringBuilder(trimSlash(server.getServerAddr())).append(path).append('?');
        boolean first = true;
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (!first) {
                url.append('&');
            }
            first = false;
            url.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
        }
        if (server.getUsername() != null && !server.getUsername().isBlank()) {
            String token = accessToken(server);
            if (token == null) {
                throw new IllegalStateException("Nacos 登录失败（检查用户名与 secretRef 指向的环境变量）");
            }
            url.append("&accessToken=").append(URLEncoder.encode(token, StandardCharsets.UTF_8));
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(url.toString()))
                .timeout(Duration.ofSeconds(5)).GET().build();
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
                .build().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String accessToken(NacosServer server) throws Exception {
        String password = server.getSecretRef() == null || server.getSecretRef().isBlank()
                ? null : System.getenv(server.getSecretRef());
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("凭据未配置: secretRef=" + server.getSecretRef()
                    + " 指向的环境变量不存在或为空");
        }
        String url = trimSlash(server.getServerAddr()) + "/nacos/v1/auth/login";
        String form = "username=" + URLEncoder.encode(server.getUsername(), StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build();
        HttpResponse<String> resp = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
                .build().send(request, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            return null;
        }
        return objectMapper.readTree(resp.body()).path("accessToken").asText(null);
    }

    private Map<String, Object> ok() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        return out;
    }

    private Map<String, Object> httpError(HttpResponse<String> resp) {
        return Map.of("success", false, "errorCode", "NACOS_HTTP_" + resp.statusCode(),
                "message", "Nacos 返回 HTTP " + resp.statusCode()
                        + (resp.body() == null || resp.body().isBlank() ? "" : ": " + truncate(resp.body())));
    }

    private String truncate(String s) {
        return s.length() > 200 ? s.substring(0, 200) + "…" : s;
    }

    private String trimSlash(String addr) {
        return addr == null ? "" : addr.replaceAll("/+$", "");
    }
}
