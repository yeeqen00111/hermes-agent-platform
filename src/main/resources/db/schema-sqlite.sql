-- HERMES Agent Platform SQLite 数据库表结构
-- 开发环境使用，生产环境用 schema.sql (MySQL)

-- Agent Profile 表
CREATE TABLE IF NOT EXISTS ai_agent_profile (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    agent_code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(500),
    system_prompt TEXT,
    model_provider VARCHAR(64),
    model_name VARCHAR(64),
    temperature DECIMAL(3,2) DEFAULT 0.7,
    max_tokens INT DEFAULT 2000,
    enabled_tools TEXT,
    tool_policy TEXT,
    timeout_ms INT DEFAULT 30000,
    memory_policy TEXT,
    knowledge_base_ids TEXT,
    enabled_skills TEXT,
    enabled_commands TEXT,
    enabled_mcp_servers TEXT,
    channel_bindings TEXT,
    data_scope_policy TEXT,
    status VARCHAR(32) DEFAULT 'DRAFT',
    current_version INT DEFAULT 1,
    del_flag TINYINT DEFAULT 0,
    create_by VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_by VARCHAR(64),
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_agent_profile_code ON ai_agent_profile(agent_code);
CREATE INDEX IF NOT EXISTS idx_agent_profile_status ON ai_agent_profile(status);

-- Agent 版本快照表
CREATE TABLE IF NOT EXISTS ai_agent_version (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    agent_code VARCHAR(64) NOT NULL,
    version INT NOT NULL,
    snapshot TEXT NOT NULL,
    publish_time DATETIME,
    publish_by VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(agent_code, version)
);

CREATE INDEX IF NOT EXISTS idx_agent_version_code ON ai_agent_version(agent_code);

-- AGENTS.MD 环境级常驻说明
CREATE TABLE IF NOT EXISTS ai_agent_context_file (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    file_type VARCHAR(32) NOT NULL,
    content TEXT NOT NULL,
    scope VARCHAR(32) DEFAULT 'GLOBAL',
    environment VARCHAR(32),
    version INT DEFAULT 1,
    del_flag TINYINT DEFAULT 0,
    create_by VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_by VARCHAR(64),
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 技能元数据表
CREATE TABLE IF NOT EXISTS ai_skill (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    skill_code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(200) NOT NULL,
    tags TEXT,
    requires_tools TEXT,
    status VARCHAR(32) DEFAULT 'ENABLED',
    current_version INT DEFAULT 1,
    del_flag TINYINT DEFAULT 0,
    create_by VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_by VARCHAR(64),
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_skill_code ON ai_skill(skill_code);
CREATE INDEX IF NOT EXISTS idx_skill_status ON ai_skill(status);

-- 技能版本快照表
CREATE TABLE IF NOT EXISTS ai_skill_version (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    skill_code VARCHAR(64) NOT NULL,
    version INT NOT NULL,
    content TEXT NOT NULL,
    requires_tools TEXT,
    author VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(skill_code, version)
);

CREATE INDEX IF NOT EXISTS idx_skill_version_code ON ai_skill_version(skill_code);

-- 指令捆绑包表
CREATE TABLE IF NOT EXISTS ai_command_bundle (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    bundle_code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(500),
    skill_codes TEXT NOT NULL,
    status VARCHAR(32) DEFAULT 'ENABLED',
    version INT DEFAULT 1,
    del_flag TINYINT DEFAULT 0,
    create_by VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_by VARCHAR(64),
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_bundle_code ON ai_command_bundle(bundle_code);

-- MCP 服务器配置表
CREATE TABLE IF NOT EXISTS ai_mcp_server (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    server_code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    base_url VARCHAR(255) NOT NULL,
    api_key_ref VARCHAR(128),
    enabled TINYINT DEFAULT 1,
    config TEXT,
    del_flag TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_mcp_server_code ON ai_mcp_server(server_code);

-- MCP 工具注册表
CREATE TABLE IF NOT EXISTS ai_mcp_tool (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    tool_code VARCHAR(128) NOT NULL UNIQUE,
    server_code VARCHAR(64) NOT NULL,
    display_name VARCHAR(128),
    param_schema TEXT,
    safety_level VARCHAR(32) DEFAULT 'READ',
    enabled TINYINT DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_mcp_tool_code ON ai_mcp_tool(tool_code);
CREATE INDEX IF NOT EXISTS idx_mcp_tool_server ON ai_mcp_tool(server_code);

-- 渠道配置表
CREATE TABLE IF NOT EXISTS ai_channel (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    channel_code VARCHAR(64) NOT NULL UNIQUE,
    channel_type VARCHAR(32) NOT NULL,
    name VARCHAR(128) NOT NULL,
    app_id VARCHAR(128),
    app_secret_ref VARCHAR(128),
    enabled TINYINT DEFAULT 1,
    status VARCHAR(32) DEFAULT 'DISCONNECTED',
    error_message VARCHAR(500),
    owner_instance VARCHAR(64),
    heartbeat_time DATETIME,
    del_flag TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_channel_code ON ai_channel(channel_code);

-- 渠道用户映射表
CREATE TABLE IF NOT EXISTS ai_channel_user (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    channel_code VARCHAR(64) NOT NULL,
    channel_user_id VARCHAR(128) NOT NULL,
    platform_user_id BIGINT NOT NULL,
    paired_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    paired_by VARCHAR(64),
    UNIQUE(channel_code, channel_user_id)
);

CREATE INDEX IF NOT EXISTS idx_channel_user_platform ON ai_channel_user(platform_user_id);

-- 模型供应商表
CREATE TABLE IF NOT EXISTS ai_model_provider (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    provider_code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    base_url VARCHAR(255) NOT NULL,
    api_key_ref VARCHAR(128),
    enabled TINYINT DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_model_provider_code ON ai_model_provider(provider_code);

-- 模型表
CREATE TABLE IF NOT EXISTS ai_model (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    provider_code VARCHAR(64) NOT NULL,
    model_name VARCHAR(64) NOT NULL,
    context_window INT,
    supports_tools TINYINT DEFAULT 0,
    enabled TINYINT DEFAULT 1,
    UNIQUE(provider_code, model_name)
);

CREATE INDEX IF NOT EXISTS idx_model_provider ON ai_model(provider_code);

-- 会话表
CREATE TABLE IF NOT EXISTS ai_chat_session (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id VARCHAR(64) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    agent_code VARCHAR(64),
    agent_version INT,
    title VARCHAR(255),
    channel VARCHAR(32),
    status VARCHAR(32) DEFAULT 'ACTIVE',
    last_message_time DATETIME,
    del_flag TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_session_id ON ai_chat_session(session_id);
CREATE INDEX IF NOT EXISTS idx_session_user ON ai_chat_session(user_id);
CREATE INDEX IF NOT EXISTS idx_session_agent ON ai_chat_session(agent_code);

-- 消息表
CREATE TABLE IF NOT EXISTS ai_chat_message (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id VARCHAR(64) NOT NULL,
    message_id VARCHAR(64) NOT NULL UNIQUE,
    role VARCHAR(32) NOT NULL,
    content TEXT,
    tool_calls TEXT,
    citations TEXT,
    trace_id VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_message_session ON ai_chat_message(session_id);
CREATE INDEX IF NOT EXISTS idx_message_id ON ai_chat_message(message_id);
CREATE INDEX IF NOT EXISTS idx_message_trace ON ai_chat_message(trace_id);

-- 工具调用审计表（只写）
CREATE TABLE IF NOT EXISTS ai_tool_call (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    trace_id VARCHAR(64) NOT NULL,
    session_id VARCHAR(64),
    agent_code VARCHAR(64),
    tool_code VARCHAR(128) NOT NULL,
    safety_level VARCHAR(32),
    arguments TEXT,
    success TINYINT,
    error_code VARCHAR(64),
    duration_ms BIGINT,
    approval_choice VARCHAR(32),
    approval_by BIGINT,
    approval_time DATETIME,
    channel VARCHAR(32),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_tool_call_trace ON ai_tool_call(trace_id);
CREATE INDEX IF NOT EXISTS idx_tool_call_session ON ai_tool_call(session_id);
CREATE INDEX IF NOT EXISTS idx_tool_call_tool ON ai_tool_call(tool_code);
CREATE INDEX IF NOT EXISTS idx_tool_call_time ON ai_tool_call(create_time);

-- 配置版本号表
CREATE TABLE IF NOT EXISTS ai_config_version (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    scope VARCHAR(32) NOT NULL UNIQUE,
    version INT NOT NULL DEFAULT 1,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_config_scope ON ai_config_version(scope);

-- 初始化配置版本号
INSERT OR IGNORE INTO ai_config_version (scope, version) VALUES
('agent', 1),
('skill', 1),
('mcp', 1),
('model', 1),
('command', 1),
('channel', 1);
