import request from './request'

export interface UserProfileVO {
  id: number
  username: string
  nickname?: string
  avatar?: string
  email?: string
  phone?: string
  gender?: number
  status?: number
  createTime?: string
  role?: string
}

export interface UserProfileUpdateDTO {
  nickname?: string
  email?: string
  phone?: string
  gender?: number
}

export interface PasswordUpdateDTO {
  oldPassword: string
  newPassword: string
  confirmPassword: string
}

export interface UserStatsVO {
  taskCount: number
  taskDone: number
  taskFailed: number
  workCount: number
  totalConsumeTime: number
}

export function getProfile() {
  return request.get<any, UserProfileVO>('/auth/profile')
}

export function updateProfile(data: UserProfileUpdateDTO) {
  return request.put<any, UserProfileVO>('/auth/profile', data)
}

export function updatePassword(data: PasswordUpdateDTO) {
  return request.put<any, void>('/auth/password', data)
}

export function uploadAvatar(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<any, { url: string }>('/auth/avatar', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function getUserStats() {
  return request.get<any, UserStatsVO>('/auth/stats')
}