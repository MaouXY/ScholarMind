import type { LoginForm, LoginResponse, UserProfile } from '@/types/auth'

import request from '@/utils/request'

const mockUsers: Record<LoginForm['role'], UserProfile> = {
  STUDENT: {
    id: '1000000000000000002',
    username: 'student01',
    nickname: 'Student One',
    email: 'student01@scholarmind.local',
    role: 'STUDENT',
    status: 1,
  },
  TEACHER: {
    id: '1000000000000000003',
    username: 'teacher01',
    nickname: 'Teacher One',
    email: 'teacher01@scholarmind.local',
    role: 'TEACHER',
    status: 1,
  },
  ADMIN: {
    id: '1000000000000000001',
    username: 'admin',
    nickname: 'System Admin',
    email: 'admin@scholarmind.local',
    role: 'ADMIN',
    status: 1,
  },
}

export function loginApi(payload: LoginForm) {
  if (import.meta.env.VITE_USE_MOCK_AUTH === 'true') {
    return Promise.resolve<LoginResponse>({
      token: `mock-token-${payload.role}`,
      expiresIn: 7200,
      user: mockUsers[payload.role],
    })
  }

  return request.post<never, LoginResponse>('/auth/login', {
    username: payload.username,
    password: payload.password,
  })
}

export function profileApi() {
  return request.get<never, UserProfile>('/user/profile')
}
