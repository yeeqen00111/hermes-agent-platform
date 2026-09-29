-- HERMES Agent Platform 数据库表结构
-- 对齐原方案 §14.2 和 agent-platform.md §13

CREATE DATABASE IF NOT EXISTS hermes_agent DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE hermes_agent;

-- ============================================
-- Agent 配置相关
-- ============================================

-- Agent Profile 表
CREATE TABLE ai_agent_profile (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    agent_code VARCHAR(64) NOT NULL UNIQUE COMMENT 'Agent编码（唯一）',
    name VARCHAR(128) NOT NULL COMMENT 'Agent名称',
    description VARCHAR(500) COMMENT '描述',
    system_prompt TEXT COMMENT '系统提示（SOUL.MD正文）',
    model_provider VARCHAR(64) COMMENT '模型供应商',
    model_name VARCHAR(64) COMMENT '模型名称',
    temperature DECIMAL(3,2) DEFAULT 0.7 COMMENT '温度',
    max_tokens INT DEFAULT 2000 COMMENT '最大Token数',
    enabled_tools JSON COMMENT '启用的工具列表',
    tool_policy JSON COMMENT '工具策略',
    timeout_ms INT DEFAULT 30000 COMMENT '超时时间(ms)',
    memory_policy JSON COMMENT '记忆策略',
    knowledge_base_ids JSON COMMENT '关联的知识库ID列表',
    enabled_skills JSON COMMENT '启用的技能列表',
    enabled_commands JSON COMMENT '启用的指令列表',
    enabled_mcp_servers JSON COMMENT '挂载的MCP服务器',
    channel_bindings JSON COMMENT '绑定的渠道',
    data_scope_policy JSON COMMENT '数据范围策略',
    status VARCHAR(32) DEFAULT 'DRAFT' COMMENT '状态: DRAFT/PUBLISHED/DEPRECATED',
    current_version INT DEFAULT 1 COMMENT '当前发布版本号',
    del_flag TINYINT DEFAULT 0 COMMENT '删除标记',
    create_by VARCHAR(64) COMMENT '创建人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(64) COMMENT '更新人',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_agent_code (agent_code),
    INDEX idx_status (status)
) ENGINE=InnoDB COMMENT='Agent配置表';

-- Agent 版本快照表（不可变）
CREATE TABLE ai_agent_version (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    agent_code VARCHAR(64) NOT NULL COMMENT 'Agent编码',
    version INT NOT NULL COMMENT '版本号',
    snapshot JSON NOT NULL COMMENT '配置快照',
    publish_time DATETIME COMMENT '发布时间',
    publish_by VARCHAR(64) COMMENT '发布人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_agent_version (agent_code, version),
    INDEX idx_agent_code (agent_code)
) ENGINE=InnoDB COMMENT='Agent版本快照表';

-- AGENTS.MD 环境级常驻说明
CREATE TABLE ai_agent_context_file (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    file_type VARCHAR(32) NOT NULL COMMENT '文件类型: AGENTS/SOUL',
    content TEXT NOT NULL COMMENT '文件内容',
    scope VARCHAR(32) DEFAULT 'GLOBAL' COMMENT '作用域: GLOBAL/ENVIRONMENT',
    environment VARCHAR(32) COMMENT '环境标识',
    version INT DEFAULT 1 COMMENT '版本号',
    del_flag TINYINT DEFAULT 0 COMMENT '删除标记',
    create_by VARCHAR(64) COMMENT '创建人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(64) COMMENT '更新人',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB COMMENT='Agent上下文文件表';

-- ============================================
-- 技能系统
-- ============================================

-- 技能元数据表
CREATE TABLE ai_skill (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    skill_code VARCHAR(64) NOT NULL UNIQUE COMMENT '技能编码',
    name VARCHAR(128) NOT NULL COMMENT '技能名称',
    description VARCHAR(200) NOT NULL COMMENT '技能描述（≤60字，进索引）',
    tags JSON COMMENT '标签',
    requires_tools JSON COMMENT '依赖的工具列表',
    status VARCHAR(32) DEFAULT 'ENABLED' COMMENT '状态: ENABLED/DISABLED',
    current_version INT DEFAULT 1 COMMENT '当前版本号',
    del_flag TINYINT DEFAULT 0 COMMENT '删除标记',
    create_by VARCHAR(64) COMMENT '创建人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(64) COMMENT '更新人',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_skill_code (skill_code),
    INDEX idx_status (status)
) ENGINE=InnoDB COMMENT='技能元数据表';

-- 技能版本快照表
CREATE TABLE ai_skill_version (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    skill_code VARCHAR(64) NOT NULL COMMENT '技能编码',
    version INT NOT NULL COMMENT '版本号',
    content TEXT NOT NULL COMMENT 'SKILL.md全文',
    requires_tools JSON COMMENT '依赖的工具列表',
    author VARCHAR(64) COMMENT '作者',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_skill_version (skill_code, version),
    INDEX idx_skill_code (skill_code)
) ENGINE=InnoDB COMMENT='技能版本快照表';

-- 指令捆绑包表
CREATE TABLE ai_command_bundle (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    bundle_code VARCHAR(64) NOT NULL UNIQUE COMMENT '捆绑包编码',
    name VARCHAR(128) NOT NULL COMMENT '捆绑包名称',
    description VARCHAR(500) COMMENT '描述',
    skill_codes JSON NOT NULL COMMENT '包含的技能编码列表',
    status VARCHAR(32) DEFAULT 'ENABLED' COMMENT '状态',
    version INT DEFAULT 1 COMMENT '版本号',
    del_flag TINYINT DEFAULT 0 COMMENT '删除标记',
    create_by VARCHAR(64) COMMENT '创建人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(64) COMMENT '更新人',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_bundle_code (bundle_code)
) ENGINE=InnoDB COMMENT='指令捆绑包表';

-- ============================================
-- MCP 扩展
-- ============================================

-- MCP 服务器配置表
CREATE TABLE ai_mcp_server (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    server_code VARCHAR(64) NOT NULL UNIQUE COMMENT '服务器编码',
    name VARCHAR(128) NOT NULL COMMENT '服务器名称',
    base_url VARCHAR(255) NOT NULL COMMENT '基础URL',
    api_key_ref VARCHAR(128) COMMENT 'API Key引用（不存明文）',
    enabled TINYINT DEFAULT 1 COMMENT '是否启用',
    config JSON COMMENT '额外配置',
    del_flag TINYINT DEFAULT 0 COMMENT '删除标记',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_server_code (server_code)
) ENGINE=InnoDB COMMENT='MCP服务器配置表';

-- MCP 工具注册表
CREATE TABLE ai_mcp_tool (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    tool_code VARCHAR(128) NOT NULL UNIQUE COMMENT '工具编码（mcp.<server>.<tool>）',
    server_code VARCHAR(64) NOT NULL COMMENT '所属服务器',
    display_name VARCHAR(128) COMMENT '展示名',
    param_schema JSON COMMENT '参数Schema',
    safety_level VARCHAR(32) DEFAULT 'READ' COMMENT '安全等级',
    enabled TINYINT DEFAULT 1 COMMENT '是否启用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_tool_code (tool_code),
    INDEX idx_server_code (server_code)
) ENGINE=InnoDB COMMENT='MCP工具注册表';

-- ============================================
-- 渠道配置
-- ============================================

-- 渠道配置表
CREATE TABLE ai_channel (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    channel_code VARCHAR(64) NOT NULL UNIQUE COMMENT '渠道编码',
    channel_type VARCHAR(32) NOT NULL COMMENT '渠道类型: FEISHU/DINGTALK/WECOM',
    name VARCHAR(128) NOT NULL COMMENT '渠道名称',
    app_id VARCHAR(128) COMMENT '应用ID',
    app_secret_ref VARCHAR(128) COMMENT '应用密钥引用',
    enabled TINYINT DEFAULT 1 COMMENT '是否启用',
    status VARCHAR(32) DEFAULT 'DISCONNECTED' COMMENT '连接状态: CONNECTED/CONNECTING/FAILED/DISCONNECTED',
    error_message VARCHAR(500) COMMENT '错误消息',
    owner_instance VARCHAR(64) COMMENT '持有连接的实例ID',
    heartbeat_time DATETIME COMMENT '最后心跳时间',
    del_flag TINYINT DEFAULT 0 COMMENT '删除标记',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_channel_code (channel_code)
) ENGINE=InnoDB COMMENT='渠道配置表';

-- 渠道用户映射表
CREATE TABLE ai_channel_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    channel_code VARCHAR(64) NOT NULL COMMENT '渠道编码',
    channel_user_id VARCHAR(128) NOT NULL COMMENT '渠道用户ID（open_id/union_id）',
    platform_user_id BIGINT NOT NULL COMMENT '平台用户ID',
    paired_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '配对时间',
    paired_by VARCHAR(64) COMMENT '配对方式: SELF/ADMIN/IMPORT',
    UNIQUE KEY uk_channel_user (channel_code, channel_user_id),
    INDEX idx_platform_user (platform_user_id)
) ENGINE=InnoDB COMMENT='渠道用户映射表';

-- ============================================
-- 模型配置
-- ============================================

-- 模型供应商表
CREATE TABLE ai_model_provider (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    provider_code VARCHAR(64) NOT NULL UNIQUE COMMENT '供应商编码',
    name VARCHAR(128) NOT NULL COMMENT '供应商名称',
    base_url VARCHAR(255) NOT NULL COMMENT '基础URL',
    api_key_ref VARCHAR(128) COMMENT 'API Key引用',
    enabled TINYINT DEFAULT 1 COMMENT '是否启用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_provider_code (provider_code)
) ENGINE=InnoDB COMMENT='模型供应商表';

-- 模型表
CREATE TABLE ai_model (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    provider_code VARCHAR(64) NOT NULL COMMENT '供应商编码',
    model_name VARCHAR(64) NOT NULL COMMENT '模型名称',
    context_window INT COMMENT '上下文窗口大小',
    supports_tools TINYINT DEFAULT 0 COMMENT '是否支持工具调用',
    enabled TINYINT DEFAULT 1 COMMENT '是否启用',
    UNIQUE KEY uk_provider_model (provider_code, model_name),
    INDEX idx_provider (provider_code)
) ENGINE=InnoDB COMMENT='模型表';

-- ============================================
-- 会话与审计
-- ============================================

-- 会话表
CREATE TABLE ai_chat_session (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    session_id VARCHAR(64) NOT NULL UNIQUE COMMENT '会话ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    agent_code VARCHAR(64) COMMENT 'Agent编码',
    agent_version INT COMMENT 'Agent版本',
    title VARCHAR(255) COMMENT '会话标题',
    channel VARCHAR(32) COMMENT '渠道来源: WEB/FEISHU/DINGTALK/WECOM',
    status VARCHAR(32) DEFAULT 'ACTIVE' COMMENT '状态',
    last_message_time DATETIME COMMENT '最后消息时间',
    del_flag TINYINT DEFAULT 0 COMMENT '删除标记',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_session_id (session_id),
    INDEX idx_user_id (user_id),
    INDEX idx_agent (agent_code)
) ENGINE=InnoDB COMMENT='会话表';

-- 消息表
CREATE TABLE ai_chat_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    session_id VARCHAR(64) NOT NULL COMMENT '会话ID',
    message_id VARCHAR(64) NOT NULL UNIQUE COMMENT '消息ID',
    role VARCHAR(32) NOT NULL COMMENT '角色: USER/ASSISTANT/SYSTEM',
    content TEXT COMMENT '消息内容',
    tool_calls JSON COMMENT '工具调用记录',
    citations JSON COMMENT '证据引用',
    trace_id VARCHAR(64) COMMENT '调用链ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_session_id (session_id),
    INDEX idx_message_id (message_id),
    INDEX idx_trace_id (trace_id)
) ENGINE=InnoDB COMMENT='消息表';

-- 工具调用审计表（只写）
CREATE TABLE ai_tool_call (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    trace_id VARCHAR(64) NOT NULL COMMENT '调用链ID',
    session_id VARCHAR(64) COMMENT '会话ID',
    agent_code VARCHAR(64) COMMENT 'Agent编码',
    tool_code VARCHAR(128) NOT NULL COMMENT '工具编码',
    safety_level VARCHAR(32) COMMENT '安全等级',
    arguments TEXT COMMENT '参数（脱敏后）',
    success TINYINT COMMENT '是否成功',
    error_code VARCHAR(64) COMMENT '错误码',
    duration_ms BIGINT COMMENT '耗时(ms)',
    approval_choice VARCHAR(32) COMMENT '审批选项',
    approval_by BIGINT COMMENT '审批人',
    approval_time DATETIME COMMENT '审批时间',
    channel VARCHAR(32) COMMENT '渠道来源',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_trace_id (trace_id),
    INDEX idx_session_id (session_id),
    INDEX idx_tool_code (tool_code),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB COMMENT='工具调用审计表';

-- ============================================
-- 配置版本管理
-- ============================================

-- 配置版本号表
CREATE TABLE ai_config_version (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    scope VARCHAR(32) NOT NULL UNIQUE COMMENT '配置作用域: agent/skill/mcp/model/command/channel',
    version INT NOT NULL DEFAULT 1 COMMENT '版本号',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_scope (scope)
) ENGINE=InnoDB COMMENT='配置版本号表';

-- ============================================
-- 初始化数据
-- ============================================

-- 初始化配置版本号
INSERT INTO ai_config_version (scope, version) VALUES
('agent', 1),
('skill', 1),
('mcp', 1),
('model', 1),
('command', 1),
('channel', 1)
ON DUPLICATE KEY UPDATE version = version;
