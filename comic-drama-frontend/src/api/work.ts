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
  zipUrl?: string
  segmentCount?: number
  duration: number
  resolution: string
  aspectRatio: string
  fileSize: number
  status: number
  isPublic: number
  viewCount: number
  likeCount: number
  shareToken?: string
  shareExpire?: string
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

export interface AssetImageItem {
  id: number
  taskId: number
  assetId?: number
  assetType?: string
  assetName?: string
  imageUrl: string
  thumbnailUrl?: string
  baseImageId?: number
  baseImageUrl?: string
  promptUsed?: string
  generateParams?: string
  status?: number
  width?: number
  height?: number
  createTime?: string
}

export function pageWork(params: WorkPageQuery) {
  return request.get<any, { records: WorkItem[]; total: number; page: number; size: number }>('/api/work/page', { params })
}

export function getWork(id: number) {
  return request.get<any, WorkItem>(`/api/work/${id}`)
}

export function getWorkByShareToken(token: string) {
  return request.get<any, WorkItem>(`/api/work/share/${token}`)
}

export function generateWorkShareToken(id: number, expireHours = 72) {
  return request.post<any, string>(`/api/work/${id}/share`, null, { params: { expireHours } })
}

export function getWorkTimeline(workId: number) {
  return request.get<any, WorkTimelineItem[]>(`/api/work/timeline/${workId}`)
}

export const createWork = (data: any) => request.post('/api/work/create', data)
export const updateWork = (data: any) => request.put('/api/work', data)
export const getWorkByTaskId = (taskId: number) => request.get(`/api/work/task/${taskId}`)

export const createWorkTimeline = (data: any) => request.post('/api/work/timeline', data)
export const updateWorkTimeline = (id: number, data: any) => request.put(`/api/work/timeline/${id}`, data)
export const deleteWorkTimeline = (id: number) => request.delete(`/api/work/timeline/${id}`)
export const listWorkTimeline = (workId: number) => request.get(`/api/work/timeline/${workId}`)
export const reorderWorkTimeline = (data: any) => request.post('/api/work/timeline/reorder', data)

export function getAssetImagesByTask(taskId: number) {
  return request.get<any, AssetImageItem[]>(`/api/workflow/asset-image/task/${taskId}`)
}

export function uploadFile(file: File, taskId?: number, sourceType?: string) {
  const formData = new FormData()
  formData.append('file', file)
  if (taskId) formData.append('taskId', String(taskId))
  if (sourceType) formData.append('sourceType', sourceType)
  return request.post<any, { id: number; fileUrl: string }>('/api/resource/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

// 场景视频（步骤7产物，数据库 scene_video 表，每段视频 URL 可直接播放）
export interface SceneVideoItem {
  id: number
  taskId: number
  sceneGroupId?: number
  videoUrl: string
  thumbnailUrl?: string
  baseFrameUrl?: string
  storyboardIds?: string
  duration?: number
  resolution?: string
  status?: number
  createTime?: string
}

// 按 taskId 拉取所有场景视频（兜底 comic_work_timeline 表不存在的情况）
export function listSceneVideosByTask(taskId: number) {
  return request.get<any, SceneVideoItem[]>(`/api/workflow/video/task/${taskId}`)
}

/**
 * 按需（懒）打包成片 ZIP。
 * @param id 作品 ID 或 任务 ID（后端内部会自动解析）
 * @param redirect true：后端返回 302，浏览器直接下载；false：后端返回 JSON 包签名 URL，前端自己跳转下载。
 *                 前端默认用 false，方便显示"打包中"loading 状态和错误提示。
 */
export function downloadWorkZip(id: number, redirect = false) {
  return request.get<any, string>(`/api/work/${id}/download-zip`, { params: { redirect } })
}
