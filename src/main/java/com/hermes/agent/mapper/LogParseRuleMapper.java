package com.hermes.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hermes.agent.entity.LogParseRule;
import org.apache.ibatis.annotations.Mapper;

/**
 * 日志 JSON 解析规则 Mapper
 */
@Mapper
public interface LogParseRuleMapper extends BaseMapper<LogParseRule> {
}
