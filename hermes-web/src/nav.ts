/**
 * 前端导航树——按白板图（智能开发运维平台）逐层还原：
 * 用户 → 主要功能 → 平台层（系统管理/功能）→ 系统层（用户/租户/配置/项目）→ Agent 配置。
 * 标记：★ 本期目标；🔁 迁移项（岚图/控制塔已有现成系统，只做适配）。
 */
export interface NavItem {
  path: string
  title: string
}

export interface NavGroup {
  title: string
  items: NavItem[]
}

export const navGroups: NavGroup[] = [
  {
    title: '智能功能',
    items: [
      { path: '/chat', title: '智能问答' },
      { path: '/review/rules', title: '代码评审 ★ · 评审规则配置' },
      { path: '/review/tasks', title: '代码评审 ★ · 评审任务' },
      { path: '/review/records', title: '代码评审 ★ · 评审记录' },
    ],
  },
  {
    title: '监控与报表',
    items: [
      { path: '/dashboard', title: '看板监控（状态/时间/时序告警）' },
      { path: '/report/half-day', title: '半天报表 · AI' },
    ],
  },
  {
    title: '智能运维（迁移项 🔁）',
    items: [
      { path: '/ops/log-alert-rules', title: '日志告警规则' },
      { path: '/ops/alert-monitor', title: '告警监控' },
      { path: '/ops/alert-send-log', title: '告警发送日志' },
      { path: '/ops/metric-monitor', title: '业务指标监控' },
      { path: '/ops/alert-report', title: '【AI】智能告警报表' },
      { path: '/ops/authorize', title: '【AI】智能运维授权' },
      { path: '/ops/log-clean', title: '日志清理（3/7/7 天）' },
    ],
  },
  {
    title: '问数 / 报表 / 知识库（迁移项 🔁）',
    items: [
      { path: '/qa', title: '智能问数' },
      { path: '/report/smart', title: '智能报表 · 千人千面' },
      { path: '/knowledge', title: '运维知识库（markdown 编辑器）' },
    ],
  },
  {
    title: '平台管理',
    items: [
      { path: '/admin/system/repo', title: '系统管理 · 代码仓库' },
      { path: '/admin/system/credential', title: '系统管理 · 凭据管理' },
      { path: '/admin/system/log-collect', title: '系统管理 · 日志采集' },
      { path: '/admin/system/nacos', title: '系统管理 · nacos 服务器' },
      { path: '/admin/org/users', title: '系统层 · 用户/角色/菜单' },
      { path: '/admin/org/tenant', title: '系统层 · 租户管理' },
      { path: '/admin/org/base-config', title: '系统层 · 基础服务层配置' },
      { path: '/admin/org/platform-config', title: '系统层 · 平台配置' },
      { path: '/admin/org/people', title: '系统层 · 人员管理' },
      { path: '/admin/org/projects', title: '系统层 · 项目管理' },
      { path: '/admin/org/iterations', title: '系统层 · 迭代管理' },
      { path: '/admin/agent/profiles', title: 'Agent · 智能体配置' },
      { path: '/admin/agent/skills', title: 'Agent · 技能' },
      { path: '/admin/agent/commands', title: 'Agent · 指令' },
      { path: '/admin/agent/models', title: 'Agent · 模型' },
      { path: '/admin/agent/channels', title: 'Agent · 渠道' },
      { path: '/admin/agent/mcp', title: 'Agent · MCP' },
    ],
  },
]
