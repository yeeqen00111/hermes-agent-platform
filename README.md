# HERMES Agent Platform（智能体基座）

单一智能体基座：平台上所有功能智能体（代码评审 / 智能运维 / 报表 / 问数）共用同一个运行时
`AgentRuntime`，彼此之间仅通过**身份包（Persona）**区分——不维护多套技术栈的 Agent。

## 单一基座 + 身份包

| 身份层 | 存储 | 注入方式 |
|---|---|---|
| SOUL（角色/使命/边界/语气） | `ai_agent_context_file` file_type=SOUL | 系统提示主体 |
| AGENTS（环境与仓库约定） | `ai_agent_context_file` file_type=AGENTS | 追加系统提示 |
| MEMORY（记忆策略+三层记忆） | `ai_agent_profile.memory_policy` + `ai_agent_memory`(AGENT/USER/SESSION) | 上下文块，按策略注入/写回 |
| USER（用户画像/数据范围） | `ai_agent_user_profile` | 按 X-Actor-User-Id 请求级注入 |
| 能力绑定 | `ai_agent_profile` enabled_tools/skills/mcp/kb/commands | 工具注册表过滤 |

执行模式（`execution_mode`）：
- `LLM_DRIVEN`：ReAct 循环（模型可发起 ```tool_call``` 围栏调用工具，最多4轮）
- `FIXED_FLOW`：确定性步骤编排（`flow_definition`，步骤类型 TOOL/LLM），每步落库
  `ai_flow_step_log`（状态/耗时/上下游/错误原因）

触发方式（`trigger_type`）：CHAT / API / SCHEDULED / WEBHOOK。
代码评审身份 `code-reviewer` 为 API+WEBHOOK+SCHEDULED 触发，不直接面向用户对话。

身份包内容为 Markdown，可通过 `/api/personas/{code}/preview` 预览组装结果，
也可导出为 SOUL.md / AGENTS.md 文件（DB 存储保证多实例一致、版本快照与热更新）。

## 代码评审模块（本期目标）

流程：触发（人工/WEBHOOK/定期扫描）→ 平台准备（仓库同步 + 提交区间 + 上次报告 + 仓库提示词）
→ 调用基座 `code-reviewer` 身份 → 报告回传（markdown + 多维评分 + 问题清单）→ 人工复审 → 闭环。

状态机：`PENDING → REVIEWING → FIRST_REVIEW_DONE → RE_REVIEWING → RE_REVIEW_DONE → CLOSED`，
失败/超时统一走失败回调置 `FAILED`（回调契约：成功与失败都必须回传）。

平台侧隔离层持有凭据（`cr_credential.secret_ref` 只存环境变量引用，明文不落库、不进日志、
错误信息脱敏），Agent 只拿到提交列表与 diff 统计。

主要 API（`/api/review`）：
- `POST /credentials`、`GET /credentials` 凭据管理
- `POST /repos`、`GET /repos`、`POST /repos/{id}/sync` 仓库下载/更新
- `POST /rules`、`GET /rules` 评审规则（触发方式/参与仓库/参与人员）
- `POST /tasks` 人工触发；`GET /tasks[?status]`、`GET /tasks/{uuid}`
- `POST /tasks/{uuid}/report`、`POST /tasks/{uuid}/failure` Agent 回传回调
- `POST /tasks/{uuid}/re-review`、`POST /tasks/{uuid}/close` 复审与闭环
- `POST /webhook` GitLab/极狐 webhook 入口
- `POST /issues/{id}/status` 问题处置（OPEN/FIXED/WONT_FIX/CLOSED）

## 其他 API

- `POST /api/chat`（SSE：message.delta / tool.start / tool.complete / citations /
  approval.request / message.complete / chat.error）、`GET /api/sessions`、
  `GET /api/sessions/{id}/messages`、`POST /api/sessions/{id}/interrupt|feedback`
- `/api/agent-profiles`、`/api/skills` 配置与发布
- `/api/personas/{code}/preview|context-files|memories|profile`、`/api/personas/user-profiles`

## 运行

开发期零外部依赖（SQLite + 无 Redis）：

```bash
mvn spring-boot:run     # 端口8081，自动建表与种子身份包
```

- 模型：`ai_model_provider` 种子 deepseek / glm，`api_key_ref` 为环境变量名
  （`DEEPSEEK_API_KEY` / `GLM_API_KEY`）；未配置密钥时 LLM Gateway 自动回退 mock，
  全流程仍可跑通。
- 生产：执行 `src/main/resources/db/schema.sql`（MySQL/OceanBase MySQL 模式），
  Docker compose 单租户多实例部署。

## 结构

```
common/      枚举（安全级别/审批选项/SSE事件）与 BaseEntity
dto/         工具请求/响应信封、SSE事件
config/      MyBatis-Plus、SQLite自动建表与增量迁移、元对象填充
persona/     身份包组装（SOUL/AGENTS/MEMORY/USER）、记忆与用户画像服务
llm/         LLM Gateway（OpenAI兼容，密钥引用，mock回退）
runtime/     AgentRuntime（LLM_DRIVEN ReAct + FIXED_FLOW 编排与步骤日志）
tool/        工具注册表/护栏/执行器/内置工具
session/     会话与历史
command/     斜杠指令路由
review/      凭据/Git仓库/评审任务编排/报告回传
api/         Chat/Agent/Skill/Persona/Review 控制器
```
