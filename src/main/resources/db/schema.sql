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
    execution_mode VARCHAR(32) DEFAULT 'LLM_DRIVEN' COMMENT '执行模式: LLM_DRIVEN/FIXED_FLOW',
    flow_definition JSON COMMENT '固定流程定义(FIXED_FLOW)',
    trigger_type VARCHAR(32) DEFAULT 'CHAT' COMMENT '触发方式: CHAT/API/SCHEDULED/WEBHOOK',
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
    scope VARCHAR(32) DEFAULT 'GLOBAL' COMMENT '作用域: GLOBAL/AGENT',
    agent_code VARCHAR(64) COMMENT '身份包归属Agent(scope=AGENT)',
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
    create_by VARCHAR(64) COMMENT '创建人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(64) COMMENT '更新人',
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
    del_flag TINYINT DEFAULT 0 COMMENT '删除标记',
    create_by VARCHAR(64) COMMENT '创建人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(64) COMMENT '更新人',
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
    create_by VARCHAR(64) COMMENT '创建人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(64) COMMENT '更新人',
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

-- ============================================
-- 身份包（Persona）：记忆 / 用户画像
-- ============================================

CREATE TABLE ai_agent_memory (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    agent_code VARCHAR(64) NOT NULL COMMENT 'Agent编码',
    scope VARCHAR(16) NOT NULL COMMENT 'AGENT/USER/SESSION',
    scope_key VARCHAR(128) NOT NULL DEFAULT '' COMMENT '作用域键',
    memory_key VARCHAR(128) NOT NULL COMMENT '记忆键',
    content TEXT NOT NULL COMMENT '记忆内容',
    source VARCHAR(32) COMMENT '来源',
    hit_count INT DEFAULT 0 COMMENT '命中次数',
    last_hit_time DATETIME COMMENT '最后命中时间',
    del_flag TINYINT DEFAULT 0 COMMENT '删除标记',
    create_by VARCHAR(64), create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_by VARCHAR(64), update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_memory (agent_code, scope, scope_key, memory_key),
    INDEX idx_memory_agent (agent_code, scope)
) ENGINE=InnoDB COMMENT='记忆存储表';

CREATE TABLE ai_agent_user_profile (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    user_id BIGINT NOT NULL UNIQUE COMMENT '用户ID',
    profile_text TEXT COMMENT '用户画像',
    preferences JSON COMMENT '偏好',
    data_scope JSON COMMENT '默认数据范围',
    del_flag TINYINT DEFAULT 0,
    create_by VARCHAR(64), create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_by VARCHAR(64), update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB COMMENT='用户画像表';

-- ============================================
-- 固定流程编排运行与步骤日志
-- ============================================

CREATE TABLE ai_flow_run (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    run_id VARCHAR(64) NOT NULL UNIQUE COMMENT '运行ID',
    agent_code VARCHAR(64) NOT NULL COMMENT 'Agent编码',
    biz_key VARCHAR(128) COMMENT '业务键',
    status VARCHAR(32) DEFAULT 'RUNNING' COMMENT 'RUNNING/SUCCESS/FAILED',
    input TEXT, output TEXT,
    error_message VARCHAR(1000),
    start_time DATETIME, end_time DATETIME, duration_ms BIGINT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_flow_run_agent (agent_code)
) ENGINE=InnoDB COMMENT='固定流程运行记录';

CREATE TABLE ai_flow_step_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    run_id VARCHAR(64) NOT NULL COMMENT '运行ID',
    step_code VARCHAR(64) NOT NULL COMMENT '步骤编码',
    step_name VARCHAR(128) COMMENT '步骤名称',
    step_type VARCHAR(32) COMMENT 'TOOL/LLM',
    upstream_step VARCHAR(64) COMMENT '上游步骤',
    status VARCHAR(32) DEFAULT 'RUNNING' COMMENT 'RUNNING/SUCCESS/FAILED',
    input TEXT, output TEXT,
    error_message VARCHAR(1000),
    start_time DATETIME, end_time DATETIME, duration_ms BIGINT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_flow_step_run (run_id)
) ENGINE=InnoDB COMMENT='固定流程步骤日志';

-- ============================================
-- 代码评审模块
-- ============================================

CREATE TABLE cr_credential (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    cred_code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    cred_type VARCHAR(32) NOT NULL COMMENT 'GIT_TOKEN/GIT_PASSWORD/API_KEY',
    username VARCHAR(128),
    secret_ref VARCHAR(256) NOT NULL COMMENT '密钥引用，不存明文',
    enabled TINYINT DEFAULT 1,
    del_flag TINYINT DEFAULT 0,
    create_by VARCHAR(64), create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_by VARCHAR(64), update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB COMMENT='代码仓库凭据表';

CREATE TABLE cr_repository (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    repo_code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    repo_url VARCHAR(500) NOT NULL,
    default_branch VARCHAR(128) DEFAULT 'master',
    credential_id BIGINT,
    workspace_dir VARCHAR(500),
    last_sync_time DATETIME,
    last_revision VARCHAR(64),
    review_prompt_extra TEXT COMMENT '仓库级评审提示词',
    enabled TINYINT DEFAULT 1,
    del_flag TINYINT DEFAULT 0,
    create_by VARCHAR(64), create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_by VARCHAR(64), update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB COMMENT='代码仓库表';

CREATE TABLE cr_review_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    rule_code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    trigger_type VARCHAR(32) NOT NULL COMMENT 'WEBHOOK/SCHEDULED/MANUAL',
    cron_expr VARCHAR(64),
    repo_ids JSON COMMENT '参与仓库',
    branch_filter VARCHAR(256),
    participant_ids JSON COMMENT '参与人员',
    enabled TINYINT DEFAULT 1,
    del_flag TINYINT DEFAULT 0,
    create_by VARCHAR(64), create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_by VARCHAR(64), update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB COMMENT='评审规则表';

CREATE TABLE cr_review_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    task_uuid VARCHAR(64) NOT NULL UNIQUE COMMENT '唯一评审任务ID',
    rule_id BIGINT,
    repo_id BIGINT NOT NULL,
    branch VARCHAR(128),
    start_revision VARCHAR(64),
    end_revision VARCHAR(64),
    status VARCHAR(32) DEFAULT 'PENDING' COMMENT 'PENDING/REVIEWING/FIRST_REVIEW_DONE/RE_REVIEWING/RE_REVIEW_DONE/CLOSED/FAILED',
    trigger_type VARCHAR(32),
    trigger_by VARCHAR(64),
    last_report_id BIGINT,
    dispatch_time DATETIME, finish_time DATETIME,
    error_message VARCHAR(1000),
    del_flag TINYINT DEFAULT 0,
    create_by VARCHAR(64), create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_by VARCHAR(64), update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_task_status (status), INDEX idx_task_repo (repo_id)
) ENGINE=InnoDB COMMENT='评审任务表';

CREATE TABLE cr_review_report (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    task_uuid VARCHAR(64) NOT NULL,
    report_markdown LONGTEXT NOT NULL,
    scores JSON COMMENT '多维评分',
    model_name VARCHAR(64),
    review_round INT DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_report_task (task_uuid)
) ENGINE=InnoDB COMMENT='评审报告表';

CREATE TABLE cr_review_issue (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    task_uuid VARCHAR(64) NOT NULL,
    report_id BIGINT,
    severity VARCHAR(32),
    category VARCHAR(64),
    title VARCHAR(500),
    file_path VARCHAR(500),
    line_no INT,
    status VARCHAR(32) DEFAULT 'OPEN' COMMENT 'OPEN/FIXED/WONT_FIX/CLOSED',
    recheck_note VARCHAR(1000),
    del_flag TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_issue_task (task_uuid)
) ENGINE=InnoDB COMMENT='评审问题表';

-- 评审规则参与人表（三角色 + 联系方式，白板「哪些人可以发起/接收报告/执行闭环」）
CREATE TABLE cr_review_participant (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    rule_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(32) NOT NULL COMMENT 'INITIATOR/RECIPIENT/CLOSER',
    email VARCHAR(128),
    feishu VARCHAR(128),
    del_flag TINYINT DEFAULT 0,
    create_by VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_by VARCHAR(64),
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_participant_rule (rule_id),
    INDEX idx_participant_user (user_id)
) ENGINE=InnoDB COMMENT='评审规则参与人表';

-- ============================================
-- 审批门：WRITE/CONTROLLED 工具的人工确认（契约 §3.3）
-- ============================================

CREATE TABLE ai_approval_request (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    request_id VARCHAR(64) NOT NULL COMMENT '审批单号（对外暴露）',
    session_id VARCHAR(64) COMMENT '所属会话（审批是会话级）',
    agent_code VARCHAR(64) COMMENT '发起的身份包',
    trace_id VARCHAR(64) COMMENT '调用链ID',
    user_id BIGINT COMMENT '发起人',
    tool_code VARCHAR(64) NOT NULL COMMENT '待执行工具',
    tool_name VARCHAR(128) COMMENT '工具展示名',
    safety_level VARCHAR(32) COMMENT 'WRITE/CONTROLLED',
    arguments TEXT COMMENT '工具参数JSON（已脱敏）',
    status VARCHAR(32) DEFAULT 'PENDING' COMMENT 'PENDING/ALLOWED/DENIED/TIMEOUT/CANCELLED',
    choice VARCHAR(32) COMMENT 'allow_once/allow_session/allow_always/deny',
    decided_by BIGINT COMMENT '审批人',
    reason VARCHAR(500) COMMENT '拒绝或撤回原因',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    expire_time DATETIME COMMENT '超时时刻',
    decide_time DATETIME COMMENT '应答时刻',
    UNIQUE KEY uk_approval_request_id (request_id),
    INDEX idx_approval_session (session_id),
    INDEX idx_approval_status (status)
) ENGINE=InnoDB COMMENT='工具审批请求表';

CREATE TABLE ai_approval_whitelist (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    tool_code VARCHAR(64) NOT NULL COMMENT '免审工具',
    user_id BIGINT NOT NULL DEFAULT 0 COMMENT '生效用户，0=全部用户',
    agent_code VARCHAR(64) COMMENT '生效身份包，空=全部',
    granted_by BIGINT NOT NULL COMMENT '谁开的白名单',
    grant_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '什么时候开的',
    enabled TINYINT DEFAULT 1,
    UNIQUE KEY uk_whitelist_tool_user (tool_code, user_id)
) ENGINE=InnoDB COMMENT='审批白名单（allow_always 落库）';

-- ============================================
-- 种子身份包与模型供应商
-- ============================================

INSERT IGNORE INTO ai_model_provider (provider_code, name, base_url, api_key_ref, enabled) VALUES
('deepseek', 'DeepSeek', 'https://api.deepseek.com', 'DEEPSEEK_API_KEY', 1),
('glm', '智谱GLM', 'https://open.bigmodel.cn/api/paas/v4', 'GLM_API_KEY', 1);

INSERT IGNORE INTO ai_model (provider_code, model_name, context_window, supports_tools, enabled) VALUES
('deepseek', 'deepseek-chat', 64000, 1, 1),
('glm', 'glm-4-flash', 128000, 1, 1);

INSERT IGNORE INTO ai_agent_profile
(agent_code, name, description, model_provider, model_name, execution_mode, trigger_type,
 enabled_tools, memory_policy, status) VALUES
('assistant', '通用运维助手', '默认对话身份：日志/告警/nacos配置查询与处置（白板运维授权面）', 'deepseek', 'deepseek-chat',
 'LLM_DRIVEN', 'CHAT',
 '["log.search","log.context","log.aggregate","alert.query","alert.acknowledge","alert.resolve","alert.suppress","nacos.change.query","nacos.config.query","nacos.instance.query","knowledge.search"]',
 '{"injectTopK":10,"writeback":true}', 'PUBLISHED'),
('code-reviewer', '代码评审专家', 'API/WEBHOOK/SCHEDULED触发的代码评审身份', 'deepseek', 'deepseek-chat',
 'LLM_DRIVEN', 'API',
 '["knowledge.search"]',
 '{"injectTopK":5,"writeback":false}', 'PUBLISHED'),
('report-analyst', '报表分析员', '固定流程编排身份：聚合日志→生成告警报表摘要', 'deepseek', 'deepseek-chat',
 'FIXED_FLOW', 'SCHEDULED',
 '["log.aggregate","alert.query"]',
 '{"injectTopK":0,"writeback":false}', 'PUBLISHED'),
('data-analyst', '问数分析员', '固定流程编排身份：受控指标查询→结果分析与解释（四期迁移适配，数据面待Java平台）', 'deepseek', 'deepseek-chat',
 'FIXED_FLOW', 'CHAT',
 '["database.metric.query","knowledge.search"]',
 '{"injectTopK":0,"writeback":false}', 'PUBLISHED');

UPDATE ai_agent_profile SET flow_definition =
 '{"steps":[{"code":"aggregate","name":"告警日志聚合","type":"TOOL","toolCode":"log.aggregate"},{"code":"summarize","name":"报表摘要生成","type":"LLM","upstream":"aggregate","promptTemplate":"你是报表分析员。基于以下聚合数据生成告警报表摘要（markdown）：\\n{{prev}}"}]}'
 WHERE agent_code = 'report-analyst';

UPDATE ai_agent_profile SET flow_definition =
 '{"steps":[{"code":"query","name":"受控指标查询","type":"TOOL","toolCode":"database.metric.query"},{"code":"analyze","name":"结果分析与解释","type":"LLM","upstream":"query","promptTemplate":"你是问数分析员。基于以下查询结果给出结论、表格与解释（markdown）：\\n{{prev}}"}]}'
 WHERE agent_code = 'data-analyst';

INSERT IGNORE INTO ai_agent_context_file (file_type, content, scope, agent_code) VALUES
('SOUL', '# SOUL\n你是HERMES平台上的通用运维助手。\n使命：帮助运维/开发人员查询日志、告警、配置并给出处置建议。\n边界：只读操作可直接执行；受控/写操作必须走审批；禁止操作不暴露。\n语气：简洁、专业、结论先行。', 'AGENT', 'assistant'),
('AGENTS', '# AGENTS\n环境约定：所有查询结果必须带引用(citations)；被截断的数据要声明truncated；时间默认使用东八区。', 'AGENT', 'assistant'),
('SOUL', '# SOUL\n你是一名资深代码评审专家。\n使命：对指定仓库分支区间内的提交进行评审，输出markdown报告与多维评分（健壮性/BUG/安全/可维护性/性能）。\n边界：只读代码；不修改仓库；问题必须给出文件与行号证据。\n流程：获取git提交信息→执行代码审查（系统提示词+仓库提示词）→报告回传。', 'AGENT', 'code-reviewer'),
('AGENTS', '# AGENTS\n评审约定：遵循仓库review_prompt_extra中的仓库级规范；评分0-100；每个问题标注severity(BLOCKER/CRITICAL/MAJOR/MINOR)与category。', 'AGENT', 'code-reviewer'),
('SOUL', '# SOUL\n你是报表分析员，按固定流程执行：聚合→摘要。不做流程外推理。', 'AGENT', 'report-analyst'),
('SOUL', '# SOUL\n你是问数分析员，按固定流程执行：指标查询→分析解释。不做流程外推理。\n输入：自然语言问题→指标与维度识别→受控查询。\n输出：表格、图表建议与解释；只读查询，不写库。', 'AGENT', 'data-analyst'),
('AGENTS', '# AGENTS\n问数约定：指标定义与SQL模板由平台语义层提供；只允许SELECT；结果须标注数据时间范围与来源指标编码。', 'AGENT', 'data-analyst');

-- ============================================
-- 三期 平台管理面：项目 / 用户（人员）
-- ============================================

CREATE TABLE IF NOT EXISTS sys_project (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    name VARCHAR(128) NOT NULL COMMENT '项目名称',
    description VARCHAR(500) COMMENT '项目描述',
    parent_id BIGINT COMMENT '所属项目',
    status VARCHAR(32) DEFAULT 'ACTIVE' COMMENT '状态',
    del_flag TINYINT DEFAULT 0 COMMENT '逻辑删除',
    create_by VARCHAR(64) COMMENT '创建人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(64) COMMENT '更新人',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB COMMENT='项目管理';

CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    user_code VARCHAR(64) NOT NULL COMMENT '用户编码（对接控制塔/PDDS身份）',
    name VARCHAR(64) NOT NULL COMMENT '姓名',
    email VARCHAR(128) COMMENT '邮箱',
    feishu VARCHAR(128) COMMENT '飞书账号',
    phone VARCHAR(32) COMMENT '手机号',
    role_codes VARCHAR(256) COMMENT '角色编码列表（本地回退位，权威在控制塔）',
    is_admin TINYINT DEFAULT 0 COMMENT '是否管理员',
    status VARCHAR(32) DEFAULT 'ACTIVE' COMMENT '状态',
    del_flag TINYINT DEFAULT 0 COMMENT '逻辑删除',
    create_by VARCHAR(64) COMMENT '创建人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(64) COMMENT '更新人',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_user_code (user_code)
) ENGINE=InnoDB COMMENT='用户/人员管理';

-- ============================================
-- 三期 ◆ 复用控制塔六项：库连接 / 告警通道 / 告警模板（平台侧集成位+本地回退）
-- ============================================

CREATE TABLE IF NOT EXISTS ai_db_connection (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    code VARCHAR(64) NOT NULL COMMENT '连接编码',
    name VARCHAR(128) NOT NULL COMMENT '连接名称',
    db_type VARCHAR(32) NOT NULL DEFAULT 'MYSQL' COMMENT '类型 MYSQL/OCEANBASE/SQLITE',
    host VARCHAR(256) NOT NULL COMMENT '主机',
    port INT NOT NULL COMMENT '端口',
    database_name VARCHAR(128) COMMENT '库名',
    username VARCHAR(128) COMMENT '用户名',
    password VARCHAR(256) COMMENT '密码',
    status VARCHAR(32) DEFAULT 'ACTIVE' COMMENT '状态',
    del_flag TINYINT DEFAULT 0 COMMENT '逻辑删除',
    create_by VARCHAR(64) COMMENT '创建人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(64) COMMENT '更新人',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_db_conn_code (code)
) ENGINE=InnoDB COMMENT='数据库连接信息（◆复用控制塔，平台侧集成位）';

CREATE TABLE IF NOT EXISTS ai_notify_channel (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    code VARCHAR(64) NOT NULL COMMENT '通道编码',
    name VARCHAR(128) NOT NULL COMMENT '通道名称',
    channel_type VARCHAR(32) NOT NULL COMMENT '类型 FEISHU/EMAIL',
    config TEXT COMMENT '通道配置JSON（飞书webhook/邮件SMTP）',
    enabled TINYINT DEFAULT 1 COMMENT '是否启用',
    del_flag TINYINT DEFAULT 0 COMMENT '逻辑删除',
    create_by VARCHAR(64) COMMENT '创建人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(64) COMMENT '更新人',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_notify_channel_code (code)
) ENGINE=InnoDB COMMENT='告警通道（飞书/邮件，◆复用控制塔，平台侧集成位）';

CREATE TABLE IF NOT EXISTS ai_notify_template (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    code VARCHAR(64) NOT NULL COMMENT '模板编码',
    name VARCHAR(128) NOT NULL COMMENT '模板名称',
    channel_type VARCHAR(32) COMMENT '适用通道类型',
    title_template VARCHAR(256) COMMENT '标题模板',
    content_template TEXT COMMENT '内容模板',
    enabled TINYINT DEFAULT 1 COMMENT '是否启用',
    del_flag TINYINT DEFAULT 0 COMMENT '逻辑删除',
    create_by VARCHAR(64) COMMENT '创建人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(64) COMMENT '更新人',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_notify_tpl_code (code)
) ENGINE=InnoDB COMMENT='告警/通知模板（◆复用控制塔，平台侧集成位）';

CREATE TABLE IF NOT EXISTS ai_notify_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    channel_code VARCHAR(64) COMMENT '通道编码',
    channel_type VARCHAR(32) COMMENT '通道类型',
    recipient VARCHAR(256) COMMENT '接收人（邮箱/飞书）',
    title VARCHAR(256) COMMENT '标题',
    content TEXT COMMENT '内容',
    status VARCHAR(32) NOT NULL COMMENT 'SUCCESS/FAILED/NO_CHANNEL',
    error VARCHAR(500) COMMENT '错误信息',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
    INDEX idx_notify_log_time (create_time)
) ENGINE=InnoDB COMMENT='通知发送日志（只写）';
