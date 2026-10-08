# hermes-agent-platform · 代码约定

> 上层约定见 ../AGENTS.md；此处只放仓库内硬约束。

- **唯一运行时**：新增智能体 = `ai_agent_profile` + `ai_agent_context_file` 配置（人格包 SOUL/AGENTS/MEMORY/USER；`execution_mode` LLM_DRIVEN|FIXED_FLOW）；禁止第二套 Agent 技术栈。
- **双 schema 同步**：建表/改列同时改 `src/main/resources/db/schema.sql` 与 `db/schema-sqlite.sql`；SQLite 迁移必须幂等。
- **凭据只存引用**：`secret_ref` 存环境变量名，库中无明文；对外输出过 sanitize（`://user:token@`→`://***@`）；Agent 不接收凭据、不执行 git 命令。
- **工具优先**：模型只经受控工具访问数据；写操作走审批门，不可逆动作不给工具。
- **判据**：同上层（Mapper+调用方，不看 DDL/枚举/实体/Service）。
- **验证**：`mvn -o compile/test`（29 项回归）；`spring-boot:run` 起在 8081，冒烟 `/api/dashboard/overview?windowHours=12`。
