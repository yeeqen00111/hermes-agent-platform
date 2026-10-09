package com.hermes.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hermes.agent.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 运维知识库节点（白板·业务层·智能运维）：
 * FOLDER（目录）/ DOC（文档，markdown 正文）。目录层级结构支持带层级的 markdown 文档编辑。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_kb_node")
public class KnowledgeNode extends BaseEntity {

    private String code;

    private String name;

    /** 父节点 ID（0=根） */
    private Long parentId;

    /** FOLDER（目录）/ DOC（文档） */
    private String nodeType;

    private String title;

    /** markdown 正文 */
    private String content;

    private Integer sortNo;

    private Integer enabled;

    private String remark;
}
