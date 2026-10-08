package com.hermes.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hermes.agent.entity.LogChannel;
import org.apache.ibatis.annotations.Mapper;

/**
 * 日志采集通道 Mapper
 */
@Mapper
public interface LogChannelMapper extends BaseMapper<LogChannel> {
}
