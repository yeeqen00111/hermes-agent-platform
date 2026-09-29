# HERMES Agent Platform

HERMES智能运维平台 - 自研智能体运行时

## 项目简介

这是HERMES智能运维平台的智能体部分，负责：
- 对话编排与SSE流式输出
- 14个受控工具的调用与审批
- Agent/技能/指令配置管理
- 渠道对话接入（飞书/钉钉/企微）
- 审计与安全防护

**注意**：本项目不包含Java平台侧的数据面功能（日志采集、告警、Nacos监控等），那些由`aiagent-backend`负责。

## 技术栈

- Java 21
- Spring Boot 3.5
- MyBatis-Plus 3.5.9
- SQLite（开发）/ MySQL（生产）

## 快速开始

### 前置要求

- JDK 21+
- Maven 3.8+

### 启动步骤

1. **克隆仓库**
```bash
git clone https://github.com/yeeqen00111/hermes-agent-platform.git
cd hermes-agent-platform
```

2. **编译打包**
```bash
mvn clean package -DskipTests
```

3. **运行应用**
```bash
java -jar target/hermes-agent-platform-0.1.0-SNAPSHOT.jar
```

或使用Maven直接运行：
```bash
mvn spring-boot:run
```

4. **访问API**
- Chat API: `POST http://localhost:8081/api/chat`
- 会话列表: `GET http://localhost:8081/api/sessions?userId=1`

### 测试示例

```bash
curl -X POST http://localhost:8081/api/chat \
  -H "Content-Type: application/json" \
  -H "X-Actor-User-Id: 1" \
  -H "X-Trace-Id: test-trace-001" \
  -d '{
    "message": "帮我查一下payment-service的错误日志",
    "agentCode": "ops-assistant"
  }'
```

## 项目结构

```
src/main/java/com/hermes/agent/
├── api/                    # REST API控制器
│   └── ChatController.java
├── command/                # 斜杠指令系统
│   └── CommandRouter.java
├── common/                 # 通用类
│   ├── enums/             # 枚举定义
│   └── BaseEntity.java
├── config/                 # 配置类
│   ├── ConfigCenter.java  # 配置版本中心
│   ├── DatabaseInitializer.java
│   └── MybatisPlusConfig.java
├── dto/                    # 数据传输对象
│   ├── ToolRequest.java
│   ├── ToolResponse.java
│   └── SSEEvent.java
├── entity/                 # 实体类
│   └── AgentProfile.java
├── mapper/                 # MyBatis Mapper
│   └── AgentProfileMapper.java
├── session/                # 会话管理
│   └── SessionManager.java
├── service/                # 业务服务
│   └── ToolCallAuditService.java
└── tool/                   # 工具系统
    ├── ToolRegistry.java
    ├── ToolExecutor.java
    ├── Guardrail.java
    ├── BuiltinTools.java
    └── ToolDefinition.java
```

## 核心设计

### 工具安全等级

| 等级 | 说明 | 示例 |
|------|------|------|
| READ | 只读操作，权限通过后执行 | log.search, alert.query |
| CONTROLLED | 需二次确认或特定权限 | report.generate, notification.send |
| WRITE | 必须人工审批 | alert.acknowledge, alert.resolve |
| FORBIDDEN | 根本不提供 | 修改生产配置、任意SQL |

### 审批机制

WRITE级工具触发审批流程，提供四个选项：
- **允许一次**：仅本次调用
- **允许本会话**：当前会话内所有匹配调用
- **始终允许**：写入白名单，跨会话持久
- **拒绝**：取消本次调用

超时无人应答时**按拒绝处理**（fail-closed）。

### 斜杠指令

三类来源：
1. **内置指令**：/new, /stop, /help, /agents, /model, /skills, /commands, /context
2. **技能指令**：安装skill后自动生成`/<name>`指令
3. **捆绑包指令**：多个技能打包成一条指令

内置指令优先于技能指令，不可被覆盖。

## 数据库

### 开发环境（SQLite）

自动创建数据库文件：`./data/hermes_agent.db`

### 生产环境（MySQL）

手动执行建表脚本：`src/main/resources/db/schema.sql`

修改`application.yml`中的数据库连接配置。

## API文档

详见[接口契约文档](../interface-contract.md)

### 主要接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/chat | 发送消息（SSE流式） |
| GET | /api/sessions | 会话列表 |
| GET | /api/sessions/{id}/messages | 历史消息 |
| POST | /api/sessions/{id}/interrupt | 停止生成 |
| POST | /api/sessions/{id}/feedback | 反馈 |

## 待办事项

- [ ] 实现LLM Gateway（对接真实模型）
- [ ] 实现Planner（执行计划生成）
- [ ] 实现Intent Router（意图识别）
- [ ] 完善Guardrail参数Schema校验
- [ ] 实现审批流程完整逻辑
- [ ] 实现Skill Registry（技能系统）
- [ ] 实现Channel Gateway（渠道长连接）
- [ ] 工具调用持久化到数据库
- [ ] 对接Java平台真实接口

## 许可证

内部项目，仅供团队使用。
