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
      redirect: '/student/library',
    },
    {
      path: '/',
      component: AppShell,
      children: [
        {
          path: 'student/library',
          name: 'student-library',
          component: () => import('@/views/student/StudentHomeView.vue'),
          meta: { role: 'STUDENT', title: '文献检索与阅读' },
        },
        {
          path: 'student/literature/:id',
          name: 'student-literature-detail',
          component: () => import('@/views/student/LiteratureDetailView.vue'),
          meta: { role: 'STUDENT', title: '文献详情' },
        },
        {
          path: 'student/literature/:id/read',
          name: 'student-literature-reader',
          component: () => import('@/views/student/LiteratureReaderView.vue'),
          meta: { role: 'STUDENT', title: '在线阅读' },
        },
        {
          path: 'student/literature/:id/qa',
          name: 'student-literature-qa',
          component: () => import('@/views/student/LiteratureQaView.vue'),
          meta: { role: 'STUDENT', title: '智能问答' },
        },
        {
          path: 'teacher/review',
          name: 'teacher-review',
          component: () => import('@/views/worker/WorkerHomeView.vue'),
          meta: { role: 'TEACHER', title: '阅读指导台' },
        },
        {
          path: 'admin/dashboard',
          name: 'admin-dashboard',
          component: () => import('@/views/admin/AdminHomeView.vue'),
          meta: { role: 'ADMIN', title: '平台管理中心' },
        },
        {
          path: 'admin/users',
          name: 'admin-users',
          component: () => import('@/views/admin/AdminUsersView.vue'),
          meta: { role: 'ADMIN', title: '用户管理' },
        },
        {
          path: 'admin/categories',
          name: 'admin-categories',
          component: () => import('@/views/admin/AdminCategoriesView.vue'),
          meta: { role: 'ADMIN', title: '分类管理' },
        },
        {
          path: 'admin/ai-logs',
          name: 'admin-ai-logs',
          component: () => import('@/views/admin/AdminAiLogsView.vue'),
          meta: { role: 'ADMIN', title: 'AI 调用日志' },
        },
        {
          path: 'admin/operations',
          name: 'admin-operations',
          component: () => import('@/views/admin/AdminOperationsView.vue'),
          meta: { role: 'ADMIN', title: '运营分析' },
        },
        {
          path: 'analytics/overview',
          name: 'analytics-overview',
          component: () => import('@/views/screen/ScreenView.vue'),
          meta: { role: 'ADMIN', title: '阅读数据总览' },
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
