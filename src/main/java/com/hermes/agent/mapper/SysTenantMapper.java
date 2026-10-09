package com.hermes.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hermes.agent.entity.SysTenant;
import org.apache.ibatis.annotations.Mapper;

/** 租户 Mapper */
@Mapper
public interface SysTenantMapper extends BaseMapper<SysTenant> {
}
