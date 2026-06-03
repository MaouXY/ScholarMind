export type UserRole = 'operator' | 'reviewer' | 'admin'

export interface LoginForm {
  username: string
  password: string
  role: UserRole
}

export interface LoginResponse {
  token: string
  role: UserRole
  displayName: string
}
