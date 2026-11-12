// 产品只保留用户端（STUDENT）与管理端（ADMIN）；教师端已下线（见 doc 10 B1）
export type UserRole = 'STUDENT' | 'ADMIN'

export type Id = string

export interface LoginForm {
  username: string
  password: string
  role: UserRole
}

export interface UserProfile {
  id: Id
  username: string
  nickname: string
  email?: string
  avatar?: string
  role: UserRole
  status: number
  createdAt?: string
  updatedAt?: string
}

export interface LoginResponse {
  token: string
  expiresIn: number
  user: UserProfile
}
