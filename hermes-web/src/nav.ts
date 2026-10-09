/**
 * 前端导航树——按白板三张图逐层还原：
 *  (1) 需求树：技术架构〔平台层（系统层/系统管理/功能）· 业务层 · Agent 层〕；
 *  (2) HERMES 架构； (3) 代码审查任务流程。
 * 用户补充（不在三张图、但指定保留）：技能 / MCP / 指令 / 智能体 / 插件 / 模型 / 渠道。
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
    title: '主要功能',
    items: [
      { path: '/chat', title: '智能问答' },
    ],
  },
  {
    title: '平台层 · 功能',
    items: [
      { path: '/review/rules', title: '代码评审 ★ · 评审规则配置' },
      { path: '/review/tasks', title: '代码评审 ★ · 评审任务' },
      { path: '/review/records', title: '代码评审 ★ · 评审记录' },
    ],
  },
  {
    title: '平台层 · 系统层',
    items: [
      { path: '/admin/org/users', title: '用户 / 角色 / 菜单' },
      { path: '/admin/org/tenant', title: '租户管理' },
      { path: '/admin/org/base-config', title: '基础服务层配置' },
      { path: '/admin/org/platform-config', title: '平台配置' },
      { path: '/admin/org/people', title: '人员管理' },
      { path: '/admin/org/projects', title: '项目管理' },
      { path: '/admin/system/repo', title: '代码仓库' },
    ],
  },
  {
    title: '平台层 · 系统管理',
    items: [
      { path: '/admin/system/credential', title: '凭据管理' },
      { path: '/admin/system/log-collect', title: '日志采集' },
      { path: '/admin/system/nacos', title: 'nacos 服务器' },
    ],
  },
  {
    title: '业务层 · 智能运维',
    items: [
      { path: '/ops/log-alert-rules', title: '日志告警规则' },
      { path: '/ops/alert-monitor', title: '告警监控' },
      { path: '/ops/alert-send-log', title: '告警发送日志' },
      { path: '/ops/alert-report', title: '【AI】智能告警报表' },
      { path: '/ops/metric-monitor', title: '业务指标监控' },
      { path: '/ops/authorize', title: '【AI】智能运维授权' },
      { path: '/ops/log-clean', title: '日志清理（3/7/7 天）' },
      { path: '/ops/evolution', title: '运维经验库（自进化）' },
      { path: '/knowledge', title: '运维知识库' },
    ],
  },
  {
    title: '业务层 · 报表 / 问数（🔁）',
    items: [
      { path: '/report/smart', title: '智能报表 · 千人千面' },
      { path: '/qa', title: '智能问数' },
    ],
  },
  {
    title: 'Agent 层 · 智能体配置（补充）',
    items: [
      { path: '/admin/agent/profiles', title: '智能体' },
      { path: '/admin/org/iterations', title: '智能体 · 迭代管理（版本/灰度）' },
      { path: '/admin/agent/skills', title: '技能' },
      { path: '/admin/agent/commands', title: '指令' },
      { path: '/admin/agent/mcp', title: 'MCP' },
      { path: '/admin/agent/plugins', title: '插件' },
      { path: '/admin/agent/models', title: '模型' },
      { path: '/admin/agent/channels', title: '渠道' },
    ],
  },
  {
    title: '平台层 · 运行审计',
    items: [
      { path: '/audit/tool-calls', title: '工具调用审计' },
    ],
  },
]
