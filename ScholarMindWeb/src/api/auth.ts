import type {
  LoginForm,
  LoginResponse,
  RegisterForm,
  UserProfile,
} from '@/types/auth'

import request from '@/utils/request'

export function loginApi(payload: LoginForm) {
  return request.post<never, LoginResponse>('/auth/login', {
    username: payload.username,
    password: payload.password,
  })
}

export function registerApi(payload: RegisterForm) {
  return request.post<never, LoginResponse>('/auth/register', {
    username: payload.username,
    password: payload.password,
    confirmPassword: payload.confirmPassword,
    role: 'STUDENT',
  })
}

export function profileApi() {
  return request.get<never, UserProfile>('/user/profile')
}
