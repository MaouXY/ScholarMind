import { createRouter, createWebHistory } from 'vue-router'

import AppShell from '@/layouts/AppShell.vue'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/login/LoginView.vue'),
      meta: { public: true, title: '登录' },
    },
    {
      path: '/',
      redirect: '/operator/workspace',
    },
    {
      path: '/',
      component: AppShell,
      children: [
        {
          path: 'operator/workspace',
          name: 'operator-workspace',
          component: () => import('@/views/student/StudentHomeView.vue'),
          meta: { role: 'operator', title: '工作区 A' },
        },
        {
          path: 'reviewer/workspace',
          name: 'reviewer-workspace',
          component: () => import('@/views/worker/WorkerHomeView.vue'),
          meta: { role: 'reviewer', title: '工作区 B' },
        },
        {
          path: 'admin/dashboard',
          name: 'admin-dashboard',
          component: () => import('@/views/admin/AdminHomeView.vue'),
          meta: { role: 'admin', title: '管理中心' },
        },
        {
          path: 'analytics/overview',
          name: 'analytics-overview',
          component: () => import('@/views/screen/ScreenView.vue'),
          meta: { role: 'admin', title: '分析概览' },
        },
      ],
    },
  ],
  scrollBehavior() {
    return { top: 0 }
  },
})

router.beforeEach((to) => {
  const authStore = useAuthStore()

  if (to.meta.title) {
    document.title = `${String(to.meta.title)} - ${import.meta.env.VITE_APP_TITLE}`
  }

  if (to.meta.public) {
    return true
  }

  if (!authStore.token) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  const requiredRole = to.meta.role as string | undefined
  if (requiredRole && authStore.role !== requiredRole) {
    return { path: authStore.defaultRoute }
  }

  return true
})

export default router
