<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { DataBoard, HomeFilled, Setting, Tools } from '@element-plus/icons-vue'

import AppSidebar from '@/components/AppSidebar.vue'
import { useAppStore } from '@/stores/app'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const authStore = useAuthStore()

const menus = computed(() => [
  {
    title: '业务分区',
    items: [
      {
        label: '工作区 A',
        path: '/operator/workspace',
        icon: HomeFilled,
        role: 'operator',
      },
    ],
  },
  {
    title: '协作分区',
    items: [
      {
        label: '工作区 B',
        path: '/reviewer/workspace',
        icon: Tools,
        role: 'reviewer',
      },
    ],
  },
  {
    title: '平台管理',
    items: [
      {
        label: '管理中心',
        path: '/admin/dashboard',
        icon: Setting,
        role: 'admin',
      },
      {
        label: '分析概览',
        path: '/analytics/overview',
        icon: DataBoard,
        role: 'admin',
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
  <div class="shell">
    <AppSidebar
      :collapsed="appStore.sidebarCollapsed"
      :menus="menus"
      :current-path="route.path"
      :role="authStore.role"
    />
    <div class="shell__main">
      <header class="shell__header">
        <div>
          <p class="shell__eyebrow">App Template</p>
          <h1>{{ route.meta.title }}</h1>
        </div>
        <div class="shell__actions">
          <el-button plain @click="appStore.toggleSidebar()">
            {{ appStore.sidebarCollapsed ? '展开导航' : '收起导航' }}
          </el-button>
          <el-dropdown>
            <span class="shell__user">
              {{ authStore.displayName }}
              <small>{{ authStore.role }}</small>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="handleLogout">
                  Sign out
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
