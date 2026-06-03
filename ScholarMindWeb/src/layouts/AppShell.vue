<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Collection,
  DataAnalysis,
  Management,
  Reading,
  Expand,
  Fold,
} from '@element-plus/icons-vue'

import AppSidebar from '@/components/AppSidebar.vue'
import { useAppStore } from '@/stores/app'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const authStore = useAuthStore()

const menus = computed(() => [
  {
    title: '学生空间',
    items: [
      {
        label: '文献检索与阅读',
        path: '/student/library',
        icon: Reading,
        role: 'STUDENT',
      },
    ],
  },
  {
    title: '教师空间',
    items: [
      {
        label: '阅读指导台',
        path: '/teacher/review',
        icon: Collection,
        role: 'TEACHER',
      },
    ],
  },
  {
    title: '平台治理',
    items: [
      {
        label: '管理中心',
        path: '/admin/dashboard',
        icon: Management,
        role: 'ADMIN',
      },
      {
        label: '数据总览',
        path: '/analytics/overview',
        icon: DataAnalysis,
        role: 'ADMIN',
      },
    ],
  },
])

function handleLogout() {
  authStore.logout()
  router.push('/login')
}
</script>

<template>
  <div class="shell" :class="{ 'shell--collapsed': appStore.sidebarCollapsed }">
    <AppSidebar
      :collapsed="appStore.sidebarCollapsed"
      :menus="menus"
      :current-path="route.path"
      :role="authStore.role"
    />
    <div class="shell__main">
      <header class="shell__header">
        <div class="shell__header-left">
          <el-button class="sidebar-toggle-btn" plain @click="appStore.toggleSidebar()">
            <el-icon>
              <Expand v-if="appStore.sidebarCollapsed" />
              <Fold v-else />
            </el-icon>
          </el-button>
          <div>
            <p class="shell__eyebrow">ScholarMind Spatial Workspace</p>
            <h1>{{ route.meta.title }}</h1>
          </div>
        </div>
        <div class="shell__actions">
          <el-dropdown>
            <span class="shell__user">
              {{ authStore.displayName }}
              <small>{{ authStore.role }}</small>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="handleLogout">
                  退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>
      <main class="shell__content">
        <router-view />
      </main>
    </div>
  </div>
</template>
