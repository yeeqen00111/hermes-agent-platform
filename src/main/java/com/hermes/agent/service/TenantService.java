package com.hermes.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hermes.agent.entity.SysTenant;
import com.hermes.agent.mapper.SysTenantMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 系统层 · 租户管理（白板）：租户实体维护。
 */
@Service
@RequiredArgsConstructor
public class TenantService {

    private final SysTenantMapper tenantMapper;

    public List<SysTenant> list() {
        return tenantMapper.selectList(new LambdaQueryWrapper<SysTenant>().orderByAsc(SysTenant::getId));
    }

    public SysTenant save(SysTenant tenant) {
        if (tenant.getStatus() == null || tenant.getStatus().isBlank()) {
            tenant.setStatus("ACTIVE");
        }
        if (tenant.getId() != null) {
            tenantMapper.updateById(tenant);
        } else {
            tenantMapper.insert(tenant);
        }
        return tenant;
    }

    public void delete(Long id) {
        tenantMapper.deleteById(id);
    }
}
