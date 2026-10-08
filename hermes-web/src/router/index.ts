import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import MainLayout from '@/layouts/MainLayout.vue'
import ChatView from '@/views/ChatView.vue'
import DashboardView from '@/views/DashboardView.vue'
import PlaceholderView from '@/views/PlaceholderView.vue'
import { navGroups } from '@/nav'

const realComponents: Record<string, unknown> = {
  '/chat': ChatView,
  '/dashboard': DashboardView,
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
