package com.hermes.agent.api;

import com.hermes.agent.auth.UserMergeService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * PDDS 单点登录对接位：SSO 网关/Java 平台按 user_code 解析（合并）平台账号。
 * 令牌契约 §6.2 方向 A；未配置令牌时开发放行并告警。
 */
@Slf4j
@RestController
@RequestMapping("/api/sso")
@RequiredArgsConstructor
public class SsoController {

    private final UserMergeService userMergeService;

    @Value("${hermes.sso.token:}")
    private String ssoToken;

    @GetMapping("/users/{userCode}")
    public Map<String, Object> lookup(@PathVariable String userCode,
                                      @RequestHeader(value = "X-Sso-Token", required = false) String token) {
        checkToken(token);
        var user = userMergeService.findByUserCode(userCode);
        if (user == null) {
            return Map.of("success", false, "message", "用户不存在: " + userCode);
        }
        return Map.of("success", true, "userId", user.getId(), "user", user);
    }

    @PostMapping("/users/resolve")
    public Map<String, Object> resolve(@RequestBody SsoIdentity identity,
                                       @RequestHeader(value = "X-Sso-Token", required = false) String token) {
        checkToken(token);
        UserMergeService.MergeResult result = userMergeService.resolve(
                identity.getUserCode(), identity.getName(), identity.getEmail(),
                identity.getPhone(), identity.getFeishu());
        return Map.of("success", true, "userId", result.userId(), "created", result.created());
    }

    private void checkToken(String token) {
        if (ssoToken != null && !ssoToken.isBlank() && !ssoToken.equals(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "SSO 令牌校验失败");
        }
        if (ssoToken == null || ssoToken.isBlank()) {
            log.warn("SSO 令牌未配置，开发放行（生产必须设置 HERMES_SSO_TOKEN）");
        }
    }

    @Data
    public static class SsoIdentity {
        private String userCode;
        private String name;
        private String email;
        private String phone;
        private String feishu;
    }
}
