# HERMES 前端（hermes-web）

智能开发运维平台前端。技术栈：**Vue 3 + TypeScript + Vite + Element Plus + Pinia + Vue Router**。

## 运行

```bash
npm install
npm run dev     # http://localhost:5173，dev server 把 /api 代理到后端 8081
npm run build   # vue-tsc 类型检查 + 生产构建
```

后端（`hermes-agent-platform`）需先起在 8081：`mvn -o spring-boot:run`。

## 结构

- `src/nav.ts` —— 导航树，逐条对应白板图（权威：仓库根 `../00-status.md` §7）
- `src/api/http.ts` / `src/api/chat.ts` —— axios 客户端 + Chat SSE 消费端（契约 §3.2）
- `src/layouts/MainLayout.vue` —— 侧边白板导航 + 顶栏
- `src/views/ChatView.vue` —— 智能问答（SSE 流式 / 工具事件 / 审批卡片）
- `src/views/ops/*` —— 智能运维（告警规则 / 告警监控 / 告警发送日志 / 日志清理 3-7-7）
- `src/views/admin/*` —— 系统层 / 系统管理（日志采集、nacos 服务器、智能体、技能、MCP、渠道、模型、仓库、凭据、人员、项目、配置、迭代）
- `src/views/PlaceholderView.vue` —— 其余屏按白板逐屏实现的占位页

## 接口契约

见仓库根 `../interface-contract.md` §3。
