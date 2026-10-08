package com.hermes.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hermes.agent.entity.NacosCategory;
import org.apache.ibatis.annotations.Mapper;

/**
 * nacos 配置分类 / 服务分类 Mapper
 */
@Mapper
public interface NacosCategoryMapper extends BaseMapper<NacosCategory> {
}
