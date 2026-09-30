package com.hermes.agent.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hermes.agent.entity.SysUser;
import com.hermes.agent.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * PDDS 单点登录用户合并对接位（白板 r0c1）：按 user_code 找平台账号，
 * 不存在则创建，存在则用 PDDS 身份更新档案（合并语义）。
 * 身份透传仍走 X-Actor-User-Id（ADR-011）；角色/菜单权威在 ◆ 控制塔。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserMergeService {

    private final SysUserMapper userMapper;

    public record MergeResult(Long userId, boolean created, SysUser user) {
    }

    public SysUser findByUserCode(String userCode) {
        return userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUserCode, userCode));
    }

    /**
     * 按 user_code 合并：返回平台 userId。
     */
    public MergeResult resolve(String userCode, String name, String email,
                               String phone, String feishu) {
        if (userCode == null || userCode.isBlank()) {
            throw new IllegalArgumentException("userCode 必填");
        }
        SysUser existing = findByUserCode(userCode);
        if (existing != null) {
            boolean changed = false;
            if (notBlank(name)) {
                existing.setName(name);
                changed = true;
            }
            if (notBlank(email)) {
                existing.setEmail(email);
                changed = true;
            }
            if (notBlank(phone)) {
                existing.setPhone(phone);
                changed = true;
            }
            if (notBlank(feishu)) {
                existing.setFeishu(feishu);
                changed = true;
            }
            if (changed) {
                userMapper.updateById(existing);
            }
            return new MergeResult(existing.getId(), false, existing);
        }
        SysUser created = new SysUser();
        created.setUserCode(userCode);
        created.setName(notBlank(name) ? name : userCode);
        created.setEmail(blankToNull(email));
        created.setPhone(blankToNull(phone));
        created.setFeishu(blankToNull(feishu));
        created.setIsAdmin(0);
        created.setStatus("ACTIVE");
        try {
            userMapper.insert(created);
        } catch (DuplicateKeyException e) {
            // 并发建号：读回已有账号
            SysUser raced = findByUserCode(userCode);
            if (raced != null) {
                return new MergeResult(raced.getId(), false, raced);
            }
            throw e;
        }
        log.info("PDDS 用户合并：新建平台账号 userCode={}, userId={}", userCode, created.getId());
        return new MergeResult(created.getId(), true, created);
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private String blankToNull(String s) {
        return notBlank(s) ? s : null;
    }
}
