import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import MainLayout from '@/layouts/MainLayout.vue'
import ChatView from '@/views/ChatView.vue'
import AuditView from '@/views/AuditView.vue'
import PlaceholderView from '@/views/PlaceholderView.vue'
import ReviewRulesView from '@/views/review/ReviewRulesView.vue'
import ReviewTasksView from '@/views/review/ReviewTasksView.vue'
import ReviewRecordsView from '@/views/review/ReviewRecordsView.vue'
import AgentProfilesView from '@/views/admin/AgentProfilesView.vue'
import SkillsView from '@/views/admin/SkillsView.vue'
import McpServersView from '@/views/admin/McpServersView.vue'
import ChannelsView from '@/views/admin/ChannelsView.vue'
import ReposView from '@/views/admin/ReposView.vue'
import CredentialsView from '@/views/admin/CredentialsView.vue'
import UsersView from '@/views/admin/UsersView.vue'
import ProjectsView from '@/views/admin/ProjectsView.vue'
import BaseConfigView from '@/views/admin/BaseConfigView.vue'
import ModelsView from '@/views/admin/ModelsView.vue'
import IterationsView from '@/views/admin/IterationsView.vue'
import LogCollectView from '@/views/admin/LogCollectView.vue'
import NacosView from '@/views/admin/NacosView.vue'
import AlertRulesView from '@/views/ops/AlertRulesView.vue'
import AlertMonitorView from '@/views/ops/AlertMonitorView.vue'
import AlertSendLogView from '@/views/ops/AlertSendLogView.vue'
import LogCleanView from '@/views/ops/LogCleanView.vue'
import MetricMonitorView from '@/views/ops/MetricMonitorView.vue'
import { navGroups } from '@/nav'

const realComponents: Record<string, unknown> = {
  '/chat': ChatView,
  '/audit/tool-calls': AuditView,
  '/review/rules': ReviewRulesView,
  '/review/tasks': ReviewTasksView,
  '/review/records': ReviewRecordsView,
  '/admin/system/repo': ReposView,
  '/admin/system/credential': CredentialsView,
  '/admin/system/log-collect': LogCollectView,
  '/admin/system/nacos': NacosView,
  '/ops/log-alert-rules': AlertRulesView,
  '/ops/alert-monitor': AlertMonitorView,
  '/ops/alert-send-log': AlertSendLogView,
  '/ops/log-clean': LogCleanView,
  '/ops/metric-monitor': MetricMonitorView,
  '/admin/agent/profiles': AgentProfilesView,
  '/admin/agent/skills': SkillsView,
  '/admin/agent/channels': ChannelsView,
  '/admin/agent/mcp': McpServersView,
  '/admin/agent/models': ModelsView,
  '/admin/org/people': UsersView,
  '/admin/org/projects': ProjectsView,
  '/admin/org/base-config': BaseConfigView,
  '/admin/org/iterations': IterationsView,
}

const children: RouteRecordRaw[] = []
for (const group of navGroups) {
  for (const item of group.items) {
    children.push({
      path: item.path.replace(/^\//, ''),
      component: realComponents[item.path] ?? PlaceholderView,
      meta: { title: item.title, group: group.title },
    } as RouteRecordRaw)
  }
}

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      component: MainLayout,
      redirect: '/chat',
      children,
    },
  ],
})

export default router
