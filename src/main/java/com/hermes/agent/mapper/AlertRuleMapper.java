package com.hermes.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hermes.agent.entity.AlertRule;
import org.apache.ibatis.annotations.Mapper;

/**
 * 日志告警规则 Mapper
 */
@Mapper
public interface AlertRuleMapper extends BaseMapper<AlertRule> {
}
