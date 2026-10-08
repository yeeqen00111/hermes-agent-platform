package com.hermes.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hermes.agent.entity.AlertRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 告警记录 Mapper
 */
@Mapper
public interface AlertRecordMapper extends BaseMapper<AlertRecord> {
}
