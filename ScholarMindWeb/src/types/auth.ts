export type UserRole = 'STUDENT' | 'TEACHER' | 'ADMIN'

export interface LoginForm {
  username: string
  password: string
  role: UserRole
}

export interface UserProfile {
  id: number
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
