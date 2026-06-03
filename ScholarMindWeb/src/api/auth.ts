import type { LoginForm, LoginResponse } from '@/types/auth'

import request from '@/utils/request'

export function loginApi(payload: LoginForm) {
  if (import.meta.env.DEV) {
    return Promise.resolve<LoginResponse>({
      token: `mock-token-${payload.role}`,
      role: payload.role,
      displayName:
        payload.role === 'admin'
          ? 'Administrator'
          : payload.role === 'reviewer'
            ? 'Reviewer'
            : 'Operator',
    })
  }

  return request.post<never, LoginResponse>('/auth/login', payload)
}
