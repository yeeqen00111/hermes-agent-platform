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
    execution_mode VARCHAR(32) DEFAULT 'LLM_DRIVEN',
    flow_definition TEXT,
    trigger_type VARCHAR(32) DEFAULT 'CHAT',
    status VARCHAR(32) DEFAULT 'DRAFT',
    current_version INT DEFAULT 1,
    gray_version INT,
    gray_ratio INT DEFAULT 0,
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
    agent_code VARCHAR(64),
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
    create_by VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_by VARCHAR(64),
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
    del_flag TINYINT DEFAULT 0,
    create_by VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_by VARCHAR(64),
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
    create_by VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_by VARCHAR(64),
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

-- 种子模型供应商（密钥只存环境变量引用，未配置时Gateway回退mock）
INSERT OR IGNORE INTO ai_model_provider (provider_code, name, base_url, api_key_ref, enabled) VALUES
('deepseek', 'DeepSeek', 'https://api.deepseek.com', 'DEEPSEEK_API_KEY', 1),
('glm', '智谱GLM', 'https://open.bigmodel.cn/api/paas/v4', 'GLM_API_KEY', 1);

INSERT OR IGNORE INTO ai_model (provider_code, model_name, context_window, supports_tools, enabled) VALUES
('deepseek', 'deepseek-chat', 64000, 1, 1),
('deepseek', 'deepseek-reasoner', 64000, 1, 1),
('glm', 'glm-4-flash', 128000, 1, 1);

-- 会话表
CREATE TABLE IF NOT EXISTS ai_chat_session (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id VARCHAR(64) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    agent_code VARCHAR(64),
    agent_version INT,
    model_override VARCHAR(128),
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

-- ============================================
-- 身份包（Persona）：记忆 / 用户画像
-- ============================================

-- 记忆存储表（三层：AGENT/USER/SESSION）
CREATE TABLE IF NOT EXISTS ai_agent_memory (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    agent_code VARCHAR(64) NOT NULL,
    scope VARCHAR(16) NOT NULL,
    scope_key VARCHAR(128) NOT NULL DEFAULT '',
    memory_key VARCHAR(128) NOT NULL,
    content TEXT NOT NULL,
    source VARCHAR(32),
    hit_count INT DEFAULT 0,
    last_hit_time DATETIME,
    del_flag TINYINT DEFAULT 0,
    create_by VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_by VARCHAR(64),
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(agent_code, scope, scope_key, memory_key)
);

CREATE INDEX IF NOT EXISTS idx_memory_agent ON ai_agent_memory(agent_code, scope);

-- 用户画像表（USER身份层）
CREATE TABLE IF NOT EXISTS ai_agent_user_profile (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id BIGINT NOT NULL UNIQUE,
    profile_text TEXT,
    preferences TEXT,
    data_scope TEXT,
    del_flag TINYINT DEFAULT 0,
    create_by VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_by VARCHAR(64),
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- ============================================
-- 固定流程编排（FIXED_FLOW）运行与步骤日志
-- ============================================

CREATE TABLE IF NOT EXISTS ai_flow_run (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    run_id VARCHAR(64) NOT NULL UNIQUE,
    agent_code VARCHAR(64) NOT NULL,
    biz_key VARCHAR(128),
    status VARCHAR(32) DEFAULT 'RUNNING',
    input TEXT,
    output TEXT,
    error_message VARCHAR(1000),
    start_time DATETIME,
    end_time DATETIME,
    duration_ms BIGINT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_flow_run_agent ON ai_flow_run(agent_code);

CREATE TABLE IF NOT EXISTS ai_flow_step_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    run_id VARCHAR(64) NOT NULL,
    step_code VARCHAR(64) NOT NULL,
    step_name VARCHAR(128),
    step_type VARCHAR(32),
    upstream_step VARCHAR(64),
    status VARCHAR(32) DEFAULT 'RUNNING',
    input TEXT,
    output TEXT,
    error_message VARCHAR(1000),
    start_time DATETIME,
    end_time DATETIME,
    duration_ms BIGINT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_flow_step_run ON ai_flow_step_log(run_id);

-- ============================================
-- 代码评审模块（本期目标）
-- ============================================

-- 凭据表：只存引用，不存明文
CREATE TABLE IF NOT EXISTS cr_credential (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    cred_code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    cred_type VARCHAR(32) NOT NULL,
    username VARCHAR(128),
    secret_ref VARCHAR(256) NOT NULL,
    enabled TINYINT DEFAULT 1,
    del_flag TINYINT DEFAULT 0,
    create_by VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_by VARCHAR(64),
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 代码仓库表
CREATE TABLE IF NOT EXISTS cr_repository (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    repo_code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    repo_url VARCHAR(500) NOT NULL,
    default_branch VARCHAR(128) DEFAULT 'master',
    credential_id BIGINT,
    workspace_dir VARCHAR(500),
    last_sync_time DATETIME,
    last_revision VARCHAR(64),
    review_prompt_extra TEXT,
    enabled TINYINT DEFAULT 1,
    del_flag TINYINT DEFAULT 0,
    create_by VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_by VARCHAR(64),
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 评审规则表（触发方式/参与仓库/参与人员）
CREATE TABLE IF NOT EXISTS cr_review_rule (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    rule_code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    trigger_type VARCHAR(32) NOT NULL,
    cron_expr VARCHAR(64),
    repo_ids TEXT,
    branch_filter VARCHAR(256),
    participant_ids TEXT,
    enabled TINYINT DEFAULT 1,
    del_flag TINYINT DEFAULT 0,
    create_by VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_by VARCHAR(64),
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 评审任务表（状态机）
CREATE TABLE IF NOT EXISTS cr_review_task (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    task_uuid VARCHAR(64) NOT NULL UNIQUE,
    rule_id BIGINT,
    repo_id BIGINT NOT NULL,
    branch VARCHAR(128),
    start_revision VARCHAR(64),
    end_revision VARCHAR(64),
    status VARCHAR(32) DEFAULT 'PENDING',
    trigger_type VARCHAR(32),
    trigger_by VARCHAR(64),
    last_report_id BIGINT,
    dispatch_time DATETIME,
    finish_time DATETIME,
    error_message VARCHAR(1000),
    del_flag TINYINT DEFAULT 0,
    create_by VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_by VARCHAR(64),
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_review_task_status ON cr_review_task(status);
CREATE INDEX IF NOT EXISTS idx_review_task_repo ON cr_review_task(repo_id);

-- 评审报告表
CREATE TABLE IF NOT EXISTS cr_review_report (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    task_uuid VARCHAR(64) NOT NULL,
    report_markdown TEXT NOT NULL,
    scores TEXT,
    model_name VARCHAR(64),
    review_round INT DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_review_report_task ON cr_review_report(task_uuid);

-- 评审问题表（复审闭环）
CREATE TABLE IF NOT EXISTS cr_review_issue (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    task_uuid VARCHAR(64) NOT NULL,
    report_id BIGINT,
    severity VARCHAR(32),
    category VARCHAR(64),
    title VARCHAR(500),
    file_path VARCHAR(500),
    line_no INT,
    status VARCHAR(32) DEFAULT 'OPEN',
    recheck_note VARCHAR(1000),
    del_flag TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_review_issue_task ON cr_review_issue(task_uuid);

-- 评审规则参与人表（三角色 + 联系方式，白板「哪些人可以发起/接收报告/执行闭环」）
CREATE TABLE IF NOT EXISTS cr_review_participant (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    rule_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(32) NOT NULL,
    email VARCHAR(128),
    feishu VARCHAR(128),
    del_flag TINYINT DEFAULT 0,
    create_by VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_by VARCHAR(64),
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_review_participant_rule ON cr_review_participant(rule_id);
CREATE INDEX IF NOT EXISTS idx_review_participant_user ON cr_review_participant(user_id);

-- ============================================
-- 审批门：WRITE/CONTROLLED 工具的人工确认（契约 §3.3）
-- ============================================

CREATE TABLE IF NOT EXISTS ai_approval_request (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    request_id VARCHAR(64) NOT NULL UNIQUE,
    session_id VARCHAR(64),
    agent_code VARCHAR(64),
    trace_id VARCHAR(64),
    user_id BIGINT,
    tool_code VARCHAR(64) NOT NULL,
    tool_name VARCHAR(128),
    safety_level VARCHAR(32),
    arguments TEXT,
    status VARCHAR(32) DEFAULT 'PENDING',
    choice VARCHAR(32),
    decided_by BIGINT,
    reason VARCHAR(500),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    expire_time DATETIME,
    decide_time DATETIME
);

CREATE INDEX IF NOT EXISTS idx_approval_session ON ai_approval_request(session_id);
CREATE INDEX IF NOT EXISTS idx_approval_status ON ai_approval_request(status);

CREATE TABLE IF NOT EXISTS ai_approval_whitelist (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    tool_code VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL DEFAULT 0,
    agent_code VARCHAR(64),
    granted_by BIGINT NOT NULL,
    grant_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    enabled TINYINT DEFAULT 1
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_whitelist_tool_user ON ai_approval_whitelist(tool_code, user_id);

-- ============================================
-- 种子身份包：单一基座上的功能智能体 = 身份配置
-- ============================================

INSERT OR IGNORE INTO ai_agent_profile
(agent_code, name, description, model_provider, model_name, execution_mode, trigger_type,
 enabled_tools, memory_policy, status) VALUES
('assistant', '通用运维助手', '默认对话身份：日志/告警/nacos配置查询与处置（白板运维授权面）', 'deepseek', 'deepseek-chat',
 'LLM_DRIVEN', 'CHAT',
 '["log.search","log.context","log.aggregate","alert.query","alert.acknowledge","alert.resolve","alert.suppress","nacos.change.query","nacos.config.query","nacos.instance.query","knowledge.search"]',
 '{"injectTopK":10,"writeback":true}', 'PUBLISHED'),
('code-reviewer', '代码评审专家', 'API/WEBHOOK/SCHEDULED触发的代码评审身份，输出markdown多维评分报告', 'deepseek', 'deepseek-chat',
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

UPDATE ai_agent_profile SET flow_definition = '{"steps":[{"code":"aggregate","name":"告警日志聚合","type":"TOOL","toolCode":"log.aggregate"},{"code":"summarize","name":"报表摘要生成","type":"LLM","upstream":"aggregate","promptTemplate":"你是报表分析员。基于以下聚合数据生成告警报表摘要（markdown）：\n{{prev}}"}]}' WHERE agent_code = 'report-analyst';

UPDATE ai_agent_profile SET flow_definition = '{"steps":[{"code":"query","name":"受控指标查询","type":"TOOL","toolCode":"database.metric.query"},{"code":"analyze","name":"结果分析与解释","type":"LLM","upstream":"query","promptTemplate":"你是问数分析员。基于以下查询结果给出结论、表格与解释（markdown）：\n{{prev}}"}]}' WHERE agent_code = 'data-analyst';

INSERT OR IGNORE INTO ai_agent_context_file (file_type, content, scope, agent_code) VALUES
('SOUL', '# SOUL
你是HERMES平台上的通用运维助手。
使命：帮助运维/开发人员查询日志、告警、配置并给出处置建议。
边界：只读操作可直接执行；受控/写操作必须走审批；禁止操作不暴露。
语气：简洁、专业、结论先行。', 'AGENT', 'assistant'),
('AGENTS', '# AGENTS
环境约定：所有查询结果必须带引用(citations)；被截断的数据要声明truncated；时间默认使用东八区。', 'AGENT', 'assistant'),
('SOUL', '# SOUL
你是一名资深代码评审专家。
使命：对指定仓库分支区间内的提交进行评审，输出markdown报告与多维评分（健壮性/BUG/安全/可维护性/性能）。
边界：只读代码；不修改仓库；问题必须给出文件与行号证据。
流程：获取git提交信息→执行代码审查（系统提示词+仓库提示词）→报告回传。', 'AGENT', 'code-reviewer'),
('AGENTS', '# AGENTS
评审约定：遵循仓库review_prompt_extra中的仓库级规范；评分0-100；每个问题标注severity(BLOCKER/CRITICAL/MAJOR/MINOR)与category。', 'AGENT', 'code-reviewer'),
('SOUL', '# SOUL
你是报表分析员，按固定流程执行：聚合→摘要。不做流程外推理。', 'AGENT', 'report-analyst'),
('SOUL', '# SOUL
你是问数分析员，按固定流程执行：指标查询→分析解释。不做流程外推理。
输入：自然语言问题→指标与维度识别→受控查询。
输出：表格、图表建议与解释；只读查询，不写库。', 'AGENT', 'data-analyst'),
('AGENTS', '# AGENTS
问数约定：指标定义与SQL模板由平台语义层提供；只允许SELECT；结果须标注数据时间范围与来源指标编码。', 'AGENT', 'data-analyst');

-- ============================================
-- 三期 平台管理面：项目 / 用户（人员）
-- ============================================

CREATE TABLE IF NOT EXISTS sys_project (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(500),
    parent_id BIGINT,
    status VARCHAR(32) DEFAULT 'ACTIVE',
    del_flag TINYINT DEFAULT 0,
    create_by VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_by VARCHAR(64),
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sys_user (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(64) NOT NULL,
    email VARCHAR(128),
    feishu VARCHAR(128),
    phone VARCHAR(32),
    role_codes VARCHAR(256),
    is_admin TINYINT DEFAULT 0,
    status VARCHAR(32) DEFAULT 'ACTIVE',
    del_flag TINYINT DEFAULT 0,
    create_by VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_by VARCHAR(64),
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- ============================================
-- 三期 ◆ 复用控制塔六项：库连接 / 告警通道 / 告警模板（平台侧集成位+本地回退）
-- ============================================

CREATE TABLE IF NOT EXISTS ai_db_connection (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    db_type VARCHAR(32) NOT NULL DEFAULT 'MYSQL',
    host VARCHAR(256) NOT NULL,
    port INT NOT NULL,
    database_name VARCHAR(128),
    username VARCHAR(128),
    password VARCHAR(256),
    status VARCHAR(32) DEFAULT 'ACTIVE',
    del_flag TINYINT DEFAULT 0,
    create_by VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_by VARCHAR(64),
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ai_notify_channel (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    channel_type VARCHAR(32) NOT NULL,
    config TEXT,
    enabled TINYINT DEFAULT 1,
    del_flag TINYINT DEFAULT 0,
    create_by VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_by VARCHAR(64),
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ai_notify_template (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    channel_type VARCHAR(32),
    title_template VARCHAR(256),
    content_template TEXT,
    enabled TINYINT DEFAULT 1,
    del_flag TINYINT DEFAULT 0,
    create_by VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_by VARCHAR(64),
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ai_notify_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    channel_code VARCHAR(64),
    channel_type VARCHAR(32),
    recipient VARCHAR(256),
    title VARCHAR(256),
    content TEXT,
    status VARCHAR(32) NOT NULL,
    error VARCHAR(500),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_notify_log_time ON ai_notify_log(create_time);

-- ============================================
-- 系统管理 · 日志采集（图2 日志中枢：filebeat→kafka→日志采集→日志解析）
-- ============================================

CREATE TABLE IF NOT EXISTS ai_log_channel (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    channel_type VARCHAR(32) NOT NULL,
    config TEXT,
    enabled TINYINT DEFAULT 1,
    remark VARCHAR(500),
    del_flag TINYINT DEFAULT 0,
    create_by VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_by VARCHAR(64),
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ai_log_parse_rule (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    channel_code VARCHAR(64),
    system_field VARCHAR(128),
    time_field VARCHAR(128),
    time_format VARCHAR(64),
    level_field VARCHAR(128),
    level_mapping TEXT,
    content_field VARCHAR(128),
    service_field VARCHAR(128),
    sample_json TEXT,
    enabled TINYINT DEFAULT 1,
    remark VARCHAR(500),
    del_flag TINYINT DEFAULT 0,
    create_by VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_by VARCHAR(64),
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_log_parse_channel ON ai_log_parse_rule(channel_code);

-- ============================================
-- 系统管理 · nacos 服务器（图2：nacos → nacos配置 → HERMES；nacos服务数量监控 → 告警）
-- ============================================

CREATE TABLE IF NOT EXISTS ai_nacos_server (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    server_addr VARCHAR(256),
    namespace_id VARCHAR(128),
    username VARCHAR(128),
    secret_ref VARCHAR(128),
    enabled TINYINT DEFAULT 1,
    remark VARCHAR(500),
    del_flag TINYINT DEFAULT 0,
    create_by VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_by VARCHAR(64),
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ai_nacos_category (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    category_type VARCHAR(32) NOT NULL,
    server_code VARCHAR(64),
    system_name VARCHAR(128),
    group_name VARCHAR(128),
    match_pattern VARCHAR(256),
    enabled TINYINT DEFAULT 1,
    remark VARCHAR(500),
    del_flag TINYINT DEFAULT 0,
    create_by VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_by VARCHAR(64),
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_nacos_category_type ON ai_nacos_category(category_type);

-- ============================================
-- 业务层 · 智能运维：日志分级-固化流程 + 告警记录 + 日志清理
-- 固化优先级：第一 告警规则 / 第二 告警白名单 / 第三 提级告警
-- ============================================

CREATE TABLE IF NOT EXISTS ai_alert_rule (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    project_name VARCHAR(128),
    rule_type VARCHAR(32) NOT NULL,
    priority INT DEFAULT 1,
    match_pattern VARCHAR(500),
    level_filter VARCHAR(128),
    channel_code VARCHAR(64),
    recipient VARCHAR(256),
    enabled TINYINT DEFAULT 1,
    remark VARCHAR(500),
    del_flag TINYINT DEFAULT 0,
    create_by VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_by VARCHAR(64),
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ai_alert_record (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    rule_code VARCHAR(64),
    project_name VARCHAR(128),
    system_name VARCHAR(128),
    server_name VARCHAR(128),
    log_level VARCHAR(32),
    content TEXT,
    log_time DATETIME,
    alert_type VARCHAR(32),
    status VARCHAR(32),
    channel_code VARCHAR(64),
    recipient VARCHAR(256),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_alert_rule_type ON ai_alert_rule(rule_type);
CREATE INDEX IF NOT EXISTS idx_alert_record_time ON ai_alert_record(create_time);

CREATE TABLE IF NOT EXISTS ai_log_retention (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    data_type VARCHAR(32) NOT NULL UNIQUE,
    retention_days INT NOT NULL,
    enabled TINYINT DEFAULT 1,
    remark VARCHAR(500)
);

CREATE TABLE IF NOT EXISTS ai_log_ingest_stat (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    project_name VARCHAR(128) NOT NULL UNIQUE,
    last_ingest_time DATETIME,
    total_ingested INT DEFAULT 0,
    total_alert INT DEFAULT 0,
    total_promote INT DEFAULT 0,
    total_suppress INT DEFAULT 0,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

INSERT OR IGNORE INTO ai_log_retention (data_type, retention_days, enabled, remark) VALUES
('FULL', 3, 1, '全量日志保留 3 天'),
('ALERT', 7, 1, '告警日志保留 7 天'),
('PROMOTE', 7, 1, '提级告警日志保留 7 天');

-- ============================================
-- 业务层 · 智能运维：业务指标监控（业务指标埋点 / 所属系统 / 指标类型）
-- ============================================

CREATE TABLE IF NOT EXISTS ai_biz_metric (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    metric_point VARCHAR(256),
    system_name VARCHAR(128),
    metric_type VARCHAR(64),
    unit VARCHAR(32),
    source_ref VARCHAR(256),
    threshold_op VARCHAR(8),
    threshold_value DOUBLE,
    channel_code VARCHAR(64),
    recipient VARCHAR(256),
    enabled TINYINT DEFAULT 1,
    remark VARCHAR(500),
    del_flag TINYINT DEFAULT 0,
    create_by VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_by VARCHAR(64),
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ai_biz_metric_sample (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    metric_code VARCHAR(64) NOT NULL,
    metric_value DOUBLE,
    breached TINYINT DEFAULT 0,
    sample_time DATETIME,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_biz_metric_sample_code ON ai_biz_metric_sample(metric_code);

-- ============================================
-- 业务层 · 智能运维：【AI】智能告警报表
-- 发送频率 / 发送内容 / 提示词 / 发送通道
-- ============================================

CREATE TABLE IF NOT EXISTS ai_alert_report (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    frequency_hours INT DEFAULT 2,
    scope VARCHAR(500),
    prompt TEXT,
    channel_code VARCHAR(64),
    recipient VARCHAR(256),
    last_send_time DATETIME,
    last_content TEXT,
    enabled TINYINT DEFAULT 1,
    remark VARCHAR(500),
    del_flag TINYINT DEFAULT 0,
    create_by VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_by VARCHAR(64),
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- ============================================
-- 业务层 · 智能运维：【AI】智能运维授权
-- 代码仓库授权 / nacos配置授权 / 日志授权（运维助手可见范围）
-- ============================================

CREATE TABLE IF NOT EXISTS ai_ops_grant (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    agent_code VARCHAR(64) NOT NULL,
    grant_type VARCHAR(32) NOT NULL,
    resource_ref VARCHAR(256),
    permission VARCHAR(16) DEFAULT 'READ',
    enabled TINYINT DEFAULT 1,
    remark VARCHAR(500),
    del_flag TINYINT DEFAULT 0,
    create_by VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_by VARCHAR(64),
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ops_grant_agent ON ai_ops_grant(agent_code);

-- ============================================
-- 业务层 · 智能运维：运维知识库（目录层级 markdown + 上传转 markdown）
-- ============================================

CREATE TABLE IF NOT EXISTS ai_kb_node (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(256) NOT NULL,
    parent_id BIGINT DEFAULT 0,
    node_type VARCHAR(16) NOT NULL,
    title VARCHAR(256),
    content TEXT,
    sort_no INT DEFAULT 0,
    enabled TINYINT DEFAULT 1,
    remark VARCHAR(500),
    del_flag TINYINT DEFAULT 0,
    create_by VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_by VARCHAR(64),
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_kb_node_parent ON ai_kb_node(parent_id);

-- ============================================
-- Agent 层 · 运维智能体「自我进化」：经验库（问题 → 解决方案）闭环
-- 记录 → 检索复用 → 反馈强化/衰减 → 定时巩固淘汰
-- ============================================

CREATE TABLE IF NOT EXISTS ai_ops_experience (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    exp_code VARCHAR(64) NOT NULL UNIQUE,
    agent_code VARCHAR(64),
    problem VARCHAR(1000) NOT NULL,
    problem_key VARCHAR(160) NOT NULL,
    cause VARCHAR(1000),
    solution TEXT,
    tags VARCHAR(500),
    system_name VARCHAR(128),
    source_type VARCHAR(32),
    source_ref VARCHAR(128),
    hits INT DEFAULT 0,
    success_count INT DEFAULT 0,
    fail_count INT DEFAULT 0,
    confidence DOUBLE DEFAULT 0.5,
    status VARCHAR(32) DEFAULT 'ACTIVE',
    last_used_time DATETIME,
    del_flag TINYINT DEFAULT 0,
    create_by VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_by VARCHAR(64),
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ops_exp_key ON ai_ops_experience(agent_code, problem_key);
CREATE INDEX IF NOT EXISTS idx_ops_exp_status ON ai_ops_experience(status);
