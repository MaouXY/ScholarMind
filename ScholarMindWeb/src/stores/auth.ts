import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import type { LoginForm, UserRole } from '@/types/auth'
import { loginApi } from '@/api/auth'
import { clearToken, getToken, setToken } from '@/utils/storage'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(getToken())
  const role = ref<UserRole>('operator')
  const displayName = ref('Template User')

  const defaultRoute = computed(() => {
    if (role.value === 'admin') {
      return '/admin/dashboard'
    }
    if (role.value === 'reviewer') {
      return '/reviewer/workspace'
    }
    return '/operator/workspace'
  })

  async function login(payload: LoginForm) {
    const response = await loginApi(payload)
    token.value = response.token
    role.value = response.role
    displayName.value = response.displayName
    setToken(response.token)
  }

  function logout() {
    token.value = ''
    role.value = 'operator'
    displayName.value = 'Template User'
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
