import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import MainLayout from '@/layouts/MainLayout.vue'
import ChatView from '@/views/ChatView.vue'
import DashboardView from '@/views/DashboardView.vue'
import PlaceholderView from '@/views/PlaceholderView.vue'
import ReviewRulesView from '@/views/review/ReviewRulesView.vue'
import ReviewTasksView from '@/views/review/ReviewTasksView.vue'
import ReviewRecordsView from '@/views/review/ReviewRecordsView.vue'
import { navGroups } from '@/nav'

const realComponents: Record<string, unknown> = {
  '/chat': ChatView,
  '/dashboard': DashboardView,
  '/review/rules': ReviewRulesView,
  '/review/tasks': ReviewTasksView,
  '/review/records': ReviewRecordsView,
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
