import type { LoginForm, LoginResponse, UserProfile } from '@/types/auth'

import request from '@/utils/request'

export function loginApi(payload: LoginForm) {
  return request.post<never, LoginResponse>('/auth/login', {
    username: payload.username,
    password: payload.password,
  })
}

export function profileApi() {
  return request.get<never, UserProfile>('/user/profile')
}
