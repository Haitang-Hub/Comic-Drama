import request from './request'

export interface DashboardStats {
  totalTasks: number
  successCount: number
  failureCount: number
  successRate: number
  failureRate: number
  avgTotalTime: number
  totalChange: number
  successChange: number
  rateChange: number
}

export interface WorkStats {
  totalWorks: number
  totalDuration: number
  resolutionDistribution: Record<string, number>
}

export interface StepTimeStats {
  stepTimes: Record<string, number>
}

export interface TrendItem {
  statDate: string
  totalTaskCount: number
  successCount: number
  failureCount: number
  successRate: number
  failureRate: number
  avgTotalTime: number
}

export function getDashboardStats() {
  return request.get<any, DashboardStats>('/api/statistics/dashboard')
}

export function getWorkStats() {
  return request.get<any, WorkStats>('/api/statistics/work')
}

export function getDailyTrend(days: number = 7) {
  return request.get<any, TrendItem[]>(`/api/statistics/trend`, { params: { days } })
}

export function getStepTimeStats() {
  return request.get<any, StepTimeStats>('/api/statistics/step-time')
}

export function triggerAggregate(date?: string) {
  return request.post<any, void>('/api/statistics/aggregate', null, { params: date ? { date } : {} })
}
