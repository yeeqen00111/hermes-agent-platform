package com.hermes.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hermes.agent.entity.LogIngestStat;
import org.apache.ibatis.annotations.Mapper;

/**
 * 日志接收/过滤统计 Mapper
 */
@Mapper
public interface LogIngestStatMapper extends BaseMapper<LogIngestStat> {
}
