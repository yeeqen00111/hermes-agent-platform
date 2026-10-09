package com.hermes.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hermes.agent.entity.AlertReport;
import org.apache.ibatis.annotations.Mapper;

/**
 * 智能告警报表配置 Mapper
 */
@Mapper
public interface AlertReportMapper extends BaseMapper<AlertReport> {
}
