package com.hermes.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hermes.agent.entity.NacosServer;
import org.apache.ibatis.annotations.Mapper;

/**
 * nacos 服务器 Mapper
 */
@Mapper
public interface NacosServerMapper extends BaseMapper<NacosServer> {
}
