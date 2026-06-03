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

export const useAuthStore = defineStore('auth', () => {
  const storedUser = getStoredUser()
  const token = ref(getToken())
  const role = ref<UserRole>(storedUser?.role ?? 'STUDENT')
  const displayName = ref(storedUser?.nickname || storedUser?.username || 'Scholar User')

  const defaultRoute = computed(() => {
    if (role.value === 'ADMIN') {
      return '/admin/dashboard'
    }
    if (role.value === 'TEACHER') {
      return '/teacher/review'
    }
    return '/student/library'
  })

  async function login(payload: LoginForm) {
    const response = await loginApi(payload)
    token.value = response.token
    role.value = response.user.role
    displayName.value = response.user.nickname || response.user.username
    setToken(response.token)
    setStoredUser(response.user)
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
