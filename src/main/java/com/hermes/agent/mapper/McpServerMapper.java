package com.hermes.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hermes.agent.entity.McpServer;
import org.apache.ibatis.annotations.Mapper;

/**
 * MCP 服务器 Mapper
 */
@Mapper
public interface McpServerMapper extends BaseMapper<McpServer> {
}
