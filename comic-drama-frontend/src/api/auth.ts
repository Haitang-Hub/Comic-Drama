import request from './request'

export interface UserInfo {
  id: number
  username: string
  nickname?: string
  avatar?: string
  email?: string
  phone?: string
  gender?: number
  status?: number
}

export interface LoginInfo {
  tokenName: string
  tokenValue: string
  userInfo: UserInfo
  roles: string[]
  permissions: string[]
}

export interface LoginDTO {
  username: string
  password: string
}

export function login(data: LoginDTO) {
  return request.post<any, LoginInfo>('/auth/login', data)
}

export function logout() {
  return request.post('/auth/logout')
}

export function getUserInfo() {
  return request.get<any, LoginInfo>('/auth/user/info')
}

export function register(data: { username: string; password: string; nickname?: string }) {
  return request.post<any, number>('/auth/register', data)
}
