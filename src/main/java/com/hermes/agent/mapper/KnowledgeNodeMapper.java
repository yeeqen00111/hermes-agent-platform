package com.hermes.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hermes.agent.entity.KnowledgeNode;
import org.apache.ibatis.annotations.Mapper;

/**
 * 运维知识库节点 Mapper
 */
@Mapper
public interface KnowledgeNodeMapper extends BaseMapper<KnowledgeNode> {
}
