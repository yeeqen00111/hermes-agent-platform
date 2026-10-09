package com.hermes.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hermes.agent.entity.BizMetricSample;
import org.apache.ibatis.annotations.Mapper;

/**
 * 业务指标采样 Mapper
 */
@Mapper
public interface BizMetricSampleMapper extends BaseMapper<BizMetricSample> {
}
