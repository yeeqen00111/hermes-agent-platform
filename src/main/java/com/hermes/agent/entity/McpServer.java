package com.hermes.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hermes.agent.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * MCP 服务器配置（扩展位：外部 MCP 服务归一化接入，§5.4/§5.5）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_mcp_server")
public class McpServer extends BaseEntity {

    private String serverCode;

    private String name;

    private String baseUrl;

    /** 环境变量名/密钥管理器键，不存明文 */
    private String apiKeyRef;

    private Integer enabled;

    /** JSON 额外配置（自定义请求头/超时等） */
    private String config;
}
