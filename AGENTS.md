# hermes-agent-platform · 代码约定

> 上层见 ../AGENTS.md，此处只放仓库内硬约束。

- **唯一运行时**：新智能体 = `ai_agent_profile` + `ai_agent_context_file` 人格包（SOUL/AGENTS/MEMORY/USER，`execution_mode` LLM_DRIVEN|FIXED_FLOW）；禁止第二套 Agent 技术栈。
- **双 schema 同步**：建表/改列同时改 `src/main/resources/db/schema.sql` 与 `db/schema-sqlite.sql`，SQLite 迁移幂等。
- **凭据只存引用**：`secret_ref` 存环境变量名，库中无明文；对外输出过 sanitize（`://user:token@`→`://***@`）。
- **工具优先**：模型只经受控工具访问数据；写操作走审批门。
- **判据 / 验证**：算实现看 Mapper + 调用方（不看 DDL/枚举/实体/Service）；`mvn -o compile|test`（29 项回归）。
