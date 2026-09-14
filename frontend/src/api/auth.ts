import { http } from './http'

export type Role = 'ADMIN' | 'OPERATIONS' | 'FINANCE' | 'CONSUMER' | 'MERCHANT' | 'RIDER'

export interface LoginResponse {
  accessToken: string
  expiresAt: string
  userId: number
  loginName: string
  roles: Role[]
}

export interface Profile {
  userId: number
  loginName: string
  roles: Role[]
}

export const authApi = {
  login: (loginName: string, password: string) =>
    http.post<LoginResponse>('/auth/login', { body: { loginName, password } }),

  logout: () => http.post<void>('/auth/logout'),

  me: () => http.get<Profile>('/auth/me')
}
