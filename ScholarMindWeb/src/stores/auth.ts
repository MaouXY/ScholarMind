import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import type { LoginForm, UserRole } from '@/types/auth'
import { loginApi } from '@/api/auth'
import {
  clearToken,
  getStoredUser,
  getToken,
  setStoredUser,
  setToken,
} from '@/utils/storage'

// 教师端已下线：历史 localStorage 里可能残留 TEACHER，统一按用户端处理
function normalizeRole(value?: string): UserRole {
  return value === 'ADMIN' ? 'ADMIN' : 'STUDENT'
}

export const useAuthStore = defineStore('auth', () => {
  const storedUser = getStoredUser()
  const token = ref(getToken())
  const role = ref<UserRole>(normalizeRole(storedUser?.role))
  const displayName = ref(storedUser?.nickname || storedUser?.username || 'Scholar User')

  const defaultRoute = computed(() => {
    if (role.value === 'ADMIN') {
      return '/admin/dashboard'
    }
    return '/student/library'
  })

  async function login(payload: LoginForm) {
    const response = await loginApi(payload)
    token.value = response.token
    role.value = normalizeRole(response.user.role)
    displayName.value = response.user.nickname || response.user.username
    setToken(response.token)
    setStoredUser({ ...response.user, role: role.value })
  }

  function logout() {
    token.value = ''
    role.value = 'STUDENT'
    displayName.value = 'Scholar User'
    clearToken()
  }

  return {
    token,
    role,
    displayName,
    defaultRoute,
    login,
    logout,
  }
})
