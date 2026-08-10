import request from './request'

export interface WorkItem {
  id: number
  workNo: string
  taskId: number
  userId: number
  title: string
  description?: string
  coverUrl: string
  videoUrl: string
  duration: number
  resolution: string
  aspectRatio: string
  fileSize: number
  status: number
  isPublic: number
  viewCount: number
  likeCount: number
  publishTime?: string
  createTime?: string
  updateTime?: string
}

export interface WorkPageQuery {
  page?: number
  size?: number
  keyword?: string
  status?: number
}

export interface WorkTimelineItem {
  id: number
  workId: number
  sceneGroupId?: number
  storyboardId?: number
  videoUrl: string
  orderIndex: number
  duration?: number
  createTime?: string
}

export function pageWork(params: WorkPageQuery) {
  return request.get<any, { records: WorkItem[]; total: number; page: number; size: number }>('/api/work/page', { params })
}

export function getWork(id: number) {
  return request.get<any, WorkItem>(`/api/work/${id}`)
}

export function getWorkTimeline(workId: number) {
  return request.get<any, WorkTimelineItem[]>(`/api/work/timeline/${workId}`)
}

export function deleteWork(id: number) {
  return request.delete(`/api/work/${id}`)
}

// 作品写操作
export const createWork = (data: any) => request.post('/api/work/create', data)
export const updateWork = (data: any) => request.put('/api/work', data)
export const getWorkByTaskId = (taskId: number) => request.get(`/api/work/task/${taskId}`)
// 时间轴
export const createWorkTimeline = (data: any) => request.post('/api/work/timeline', data)
export const updateWorkTimeline = (id: number, data: any) => request.put(`/api/work/timeline/${id}`, data)
export const deleteWorkTimeline = (id: number) => request.delete(`/api/work/timeline/${id}`)
export const listWorkTimeline = (workId: number) => request.get(`/api/work/timeline/${workId}`)
export const reorderWorkTimeline = (data: any) => request.post('/api/work/timeline/reorder', data)
