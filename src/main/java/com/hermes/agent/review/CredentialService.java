package com.hermes.agent.review;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hermes.agent.entity.CrCredential;
import com.hermes.agent.mapper.CrCredentialMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 凭据管理：数据库只存引用，明文仅从环境变量/密钥管理器按需解析，不落库不出现在日志
 */
@Service
@RequiredArgsConstructor
public class CredentialService {

    private final CrCredentialMapper credentialMapper;

    public List<CrCredential> list() {
        return credentialMapper.selectList(new LambdaQueryWrapper<CrCredential>()
                .orderByDesc(CrCredential::getId));
    }

    public CrCredential save(CrCredential credential) {
        if (credential.getId() != null) {
            credentialMapper.updateById(credential);
        } else {
            credentialMapper.insert(credential);
        }
        return credential;
    }

    public CrCredential get(Long id) {
        return id == null ? null : credentialMapper.selectById(id);
    }

    /**
     * 解析凭据明文（仅内存使用）
     */
    public ResolvedCredential resolve(Long credentialId) {
        CrCredential cred = get(credentialId);
        if (cred == null || cred.getEnabled() == null || cred.getEnabled() == 0) {
            return null;
        }
        String secret = System.getenv(cred.getSecretRef());
        if (secret == null || secret.isBlank()) {
            return null;
        }
        return new ResolvedCredential(cred.getUsername(), secret);
    }

    public record ResolvedCredential(String username, String secret) {
    }
}
