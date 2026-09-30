package com.hermes.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hermes.agent.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * MCP 工具归一化注册表（tool_code = mcp.<server>.<tool>，§5.4）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_mcp_tool")
public class McpTool extends BaseEntity {

    private String toolCode;

    private String serverCode;

    private String displayName;

    /** JSON Schema 文本 */
    private String paramSchema;

    private String safetyLevel;

    private Integer enabled;
}
