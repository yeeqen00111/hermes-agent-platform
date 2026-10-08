package com.hermes.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hermes.agent.entity.LogRetention;
import org.apache.ibatis.annotations.Mapper;

/**
 * 日志保留策略 Mapper
 */
@Mapper
public interface LogRetentionMapper extends BaseMapper<LogRetention> {
}
