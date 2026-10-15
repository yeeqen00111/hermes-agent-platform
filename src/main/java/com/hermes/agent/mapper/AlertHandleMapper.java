package com.hermes.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hermes.agent.entity.AlertHandle;
import org.apache.ibatis.annotations.Mapper;

/**
 * 告警处理流水 Mapper
 */
@Mapper
public interface AlertHandleMapper extends BaseMapper<AlertHandle> {
}
