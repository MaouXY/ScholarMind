import type { UserProfile } from '@/types/auth'

const TOKEN_KEY = 'scholar-mind-token'
const USER_KEY = 'scholar-mind-user'

export function getToken() {
  return localStorage.getItem(TOKEN_KEY) ?? ''
}

export function setToken(token: string) {
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

export function getStoredUser() {
  const raw = localStorage.getItem(USER_KEY)
  if (!raw) {
    return null
  }

  try {
    return JSON.parse(raw) as UserProfile
  } catch (error) {
    console.error('local user parse failed, scenario=auth-restore, userKey=%s', USER_KEY, error)
    localStorage.removeItem(USER_KEY)
    return null
  }
}

export function setStoredUser(user: UserProfile) {
  localStorage.setItem(USER_KEY, JSON.stringify(user))
}
