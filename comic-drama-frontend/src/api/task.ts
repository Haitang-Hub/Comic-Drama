import request from './request'

export interface TaskItem {
  id: string
  taskNo: string
  userId: string
  title?: string
  storyRequirement: string
  duration: number
  aspectRatio?: string
  resolution?: string
  voiceEnabled?: number
  /** 执行模式：0全自动 1人工审核 */
  execMode?: number
  artStyle?: string
  visualStyle?: string
  status: number
  currentStep?: number
  progress: number
  retryCount?: number
  queuePosition?: number
  coverUrl?: string
  finalVideoUrl?: string
  createTime?: string
  updateTime?: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}

export interface TaskCreateDTO {
  title?: string
  storyRequirement: string
  duration?: number
  aspectRatio?: string
  resolution?: string
  voiceEnabled?: number
  /** 执行模式：0全自动 1人工审核 */
  execMode?: number
  /** 画风（基础视觉技法） */
  artStyle?: string
  /** 风格（美学取向/文化调性） */
  visualStyle?: string
  /** 高级风格参数（与后端 TaskCreateDTO 对齐） */
  artStyleStrength?: number
  characterSimilarityWeight?: number
  imageClarity?: number
  saturation?: number
  lightIntensity?: number
  voiceSpeed?: number
  emotionIntensity?: number
  videoSmoothness?: number
  frameInheritStrength?: number
  remark?: string
}

export interface TaskPageQuery {
  page?: number
  size?: number
  keyword?: string
  status?: number
  queryAll?: boolean
}

/** 资产设计 VO（step 3） */
export interface AssetDesignVO {
  id?: number
  taskId?: string
  assetType: string
  /** 基础资产名（无版本标识，用于归组） */
  baseAssetName?: string
  /** 衍生自（上一版本资产名，无则写"无"） */
  derivedFrom?: string
  assetName: string
  assetDesc?: string
  resourceUrl?: string
  /** 版本（从1开始） */
  version?: number
  createTime?: string
}

/** 资产图片 VO（step 4） */
export interface AssetImageVO {
  id?: number
  taskId?: string
  assetId?: string
  assetType: string
  assetName: string
  imageUrl: string
  thumbnailUrl?: string
  width?: number
  height?: number
  promptUsed?: string
  status?: number
  createTime?: string
}

/** 故事大纲 VO（step 1） */
export interface StoryOutlineVO {
  id?: number
  outlineText: string
  summary?: string
  wordCount?: number
  /** 正面提示词（步骤1 AI生成，供步骤4/5/6使用） */
  positivePrompt?: string
  /** 负面提示词（步骤1 AI生成，供步骤4/5/6使用） */
  negativePrompt?: string
  createTime?: string
}

/** 场景分组 VO（step 2） */
export interface SceneGroupVO {
  id?: number
  groupIndex: number
  title: string
  description?: string
  sceneCount?: number
  duration?: number
  createTime?: string
}

/** 分镜脚本 VO（step 2） */
export interface StoryboardVO {
  id?: number
  sceneGroupId?: number
  sceneIndex: number
  localSeq?: number
  shotType?: string
  location?: string
  timeOfDay?: string
  characters?: string
  action?: string
  dialogue?: string
  emotion?: string
  cameraAngle?: string
  cameraMovement?: string
  /** 场景（场景名称_版本标识，分号分隔） */
  scene?: string
  /** 出场道具（道具名称_版本标识，分号分隔，无则写"无"） */
  props?: string
  soundEffect?: string
  bgm?: string
  duration?: number
  prompt?: string
  shotDesc?: string
  storyboardDesc?: string
  visualDesc?: string
  createTime?: string
}

/** 分镜画面 VO（step 4） */
export interface StoryboardImageVO {
  id?: number
  sceneIndex: number
  imageUrl: string
  thumbnailUrl?: string
  width?: number
  height?: number
  seed?: number
  prompt?: string
  createTime?: string
}

/** 分镜音频 VO（step 5） */
export interface StoryboardAudioVO {
  id?: number
  sceneIndex: number
  roleName?: string
  audioUrl: string
  duration?: number
  voiceName?: string
  speed?: number
  emotion?: string
  createTime?: string
}

/** 场景视频 VO（step 6/7） */
export interface SceneVideoVO {
  id?: number
  sceneIndex: number
  videoUrl: string
  coverUrl?: string
  duration?: number
  resolution?: string
  fileSize?: number
  createTime?: string
}

/** 任务进度日志 */
export interface TaskProgressLogVO {
  id?: string
  taskId: string
  step: number
  stepName?: string
  progress: number
  status?: number
  message?: string
  createTime?: string
}

/** 任务失败日志 */
export interface TaskFailureLogVO {
  id?: string
  taskId: string
  step: number
  stepName?: string
  nodeType?: string
  nodeKey?: string
  modelName?: string
  errorType?: string
  errorCode?: string
  errorMessage?: string
  errorStack?: string
  stackTrace?: string
  retryCount?: number
  resolved?: number
  createTime?: string
}

/** 节点状态 */
export interface TaskNodeStateVO {
  id?: string
  taskId: string
  nodeCode?: string
  nodeName?: string
  step?: number
  status: number
  startTime?: string
  endTime?: string
  duration?: number
  durationMs?: number
  inputPayload?: string
  outputPayload?: string
  inputSnapshot?: string
  outputSnapshot?: string
  errorMessage?: string
  errorMsg?: string
  retryCount?: number
  regenerateCount?: number
  createTime?: string
}

/** 任务详情 VO */
export interface TaskDetailVO {
  id: string
  taskNo: string
  title: string
  storyRequirement: string
  status: number
  statusText: string
  currentStep: number
  progress: number
  duration: number
  aspectRatio: string
  resolution: string
  voiceEnabled: number
  /** 执行模式：0全自动 1人工审核 */
  execMode?: number
  /** 是否处于审核暂停态（人工审核模式 + 当前步骤已完成等待审核） */
  pendingReview?: boolean
  artStyle?: string
  visualStyle?: string
  failureStep?: number
  failureReason?: string
  failureDetail?: string
  createTime: string
  startTime?: string
  endTime?: string
  totalConsumeTime?: number
  finalVideoUrl?: string
  coverUrl?: string
  /** 成片 manifest.json 内容（包含视频片段列表，用于在线播放） */
  finalWorkManifest?: string
  outline?: StoryOutlineVO
  sceneGroups?: SceneGroupVO[]
  storyboards?: StoryboardVO[]
  assetDesigns?: AssetDesignVO[]
  /** 步骤4：资产绘图（首版资产图，baseImageId 为空） */
  assetImages?: AssetImageVO[]
  /** 步骤5：衍生绘图（衍生资产图，基于首版资产图衍生，baseImageId 非空） */
  deriveImages?: AssetImageVO[]
  images?: StoryboardImageVO[]
  audios?: StoryboardAudioVO[]
  videos?: SceneVideoVO[]
  progressLogs?: TaskProgressLogVO[]
  failureLogs?: TaskFailureLogVO[]
  nodeStates?: TaskNodeStateVO[]
}

export function createTask(data: TaskCreateDTO) {
  return request.post<any, TaskItem>('/api/task', data)
}

export function pageTask(params: TaskPageQuery) {
  return request.get<any, PageResult<TaskItem>>('/api/task/page', { params })
}

export function getTask(id: string) {
  return request.get<any, TaskItem>(`/api/task/${id}`)
}

/** 获取任务详情（含 7 步产物、进度日志、节点状态、失败日志） */
export function getTaskDetail(id: string) {
  return request.get<any, TaskDetailVO>(`/api/task/${id}`)
}

/** 获取任务实时进度日志 */
export function getTaskProgress(id: string) {
  return request.get<any, TaskProgressLogVO[]>(`/api/task/${id}/progress`)
}

/** 获取任务各节点状态 */
export function getTaskNodeStates(id: string) {
  return request.get<any, TaskNodeStateVO[]>(`/api/task/${id}/nodes`)
}

/** 获取任务失败日志 */
export function getTaskFailureLogs(id: string) {
  return request.get<any, TaskFailureLogVO[]>(`/api/failure/list`, { params: { taskId: id } })
}

/** 清空任务失败日志 */
export function clearTaskFailureLogs(id: string) {
  return request.delete(`/api/failure/clear`, { params: { taskId: id } })
}

export function deleteTask(id: string) {
  return request.delete(`/api/task/${id}`)
}

export function pauseTask(id: string, rollbackCurrentStep = false, stopAfterCurrentStep = false) {
  return request.put(`/api/task/${id}/pause`, null, { params: { rollbackCurrentStep, stopAfterCurrentStep } })
}

/**
 * 获取任务的成片播放清单 manifest.json。
 * - 若任务已保存 manifest（VideoMergeStepHandler 生成的最终版），直接返回
 * - 否则后端从 scene_video 实时构建并回写 DB（兼容历史任务）
 * 返回字符串形式的 JSON（含 videos 数组），前端需再 JSON.parse 取 data
 */
export function getTaskManifest(id: string, silentError: boolean = true) {
  return request.get<any, string>(`/api/task/${id}/manifest`, {
    headers: silentError ? { 'X-Silent-Error': '1' } : undefined
  })
}

export function resumeTask(id: string) {
  return request.put(`/api/task/${id}/resume`)
}

export function retryTask(id: string) {
  return request.put(`/api/task/${id}/retry`)
}

export function regenerateNode(taskId: string, stepOrder: number, params?: Record<string, any>) {
  return request.post(`/api/task/${taskId}/regenerate`, params || {}, { params: { stepOrder } })
}

export function resumeFromFailure(taskId: string) {
  return request.post(`/api/task/${taskId}/resume-from-failure`)
}

export function resumeFromStep(taskId: string, stepOrder: number) {
  return request.post(`/api/task/${taskId}/resume-from-step`, null, { params: { stepOrder } })
}

/** 审核通过：人工审核模式下继续执行下一步 */
export function approveTask(taskId: string) {
  return request.post(`/api/task/${taskId}/approve`)
}

/** 执行下一步骤（通用"继续"语义：不限制 execMode，单步执行后再次暂停） */
export function executeNextStep(taskId: string) {
  return request.post(`/api/task/${taskId}/next-step`)
}

/** 单张资产图重生成（步骤4 首版资产图 / 步骤5 衍生资产图 通用，按 imageId 自动判定归属） */
export function regenerateAssetImage(taskId: string, imageId: string | number, params?: Record<string, any>) {
  return request.post(`/api/task/${taskId}/regenerate/asset-image/${imageId}`, params || {})
}

/** 单张分镜图重生成（步骤6） */
export function regenerateStoryboardImage(taskId: string, imageId: string | number, params?: Record<string, any>) {
  return request.post(`/api/task/${taskId}/regenerate/storyboard-image/${imageId}`, params || {})
}

/** 单条场景视频重生成（步骤8） */
export function regenerateSceneVideo(taskId: string, videoId: string | number, params?: Record<string, any>) {
  return request.post(`/api/task/${taskId}/regenerate/scene-video/${videoId}`, params || {})
}

export const getQueuePage = (params: any) => request.get('/api/queue/page', { params })
export const getFailurePage = (params: any) => request.get('/api/failure/page', { params })
export const getDailyStatsPage = (params: any) => request.get('/api/statistics/daily/page', { params })

// ======== 人工审核：手动修改已完成产物 ========

/** 更新故事摘要（步骤1） */
export function updateStorySummary(taskId: string, content: string) {
  return request.put('/api/workflow/pipeline/artifacts/summary', { taskId, outlineText: content, summary: content })
}

/** 更新单条分镜脚本（步骤2） */
export function updateStoryboard(taskId: string, storyboardId: string | number, fields: Record<string, any>) {
  return request.put('/api/workflow/pipeline/artifacts/storyboard', { taskId, storyboardId, fields })
}

/** 更新资产设计（步骤3） */
export function updateAssetDesign(taskId: string, assetId: string | number, fields: Record<string, any>) {
  return request.put('/api/workflow/pipeline/artifacts/asset-design', { taskId, assetId, fields })
}

/** 替换资产/衍生图片（步骤4/5） */
export function replaceAssetImage(taskId: string, imageId: string | number, newImageUrl: string, newThumbnailUrl?: string) {
  return request.put('/api/workflow/pipeline/artifacts/asset-image', { taskId, imageId, newImageUrl, newThumbnailUrl })
}

/** 替换分镜图片（步骤6） */
export function replaceStoryboardImage(taskId: string, imageId: string | number, newImageUrl: string, newThumbnailUrl?: string) {
  return request.put('/api/workflow/pipeline/artifacts/storyboard-image', { taskId, imageId, newImageUrl, newThumbnailUrl })
}

/** 替换配音文件（步骤7） */
export function replaceAudio(taskId: string, audioId: string | number, newAudioUrl: string) {
  return request.put('/api/workflow/pipeline/artifacts/audio', { taskId, audioId, newAudioUrl })
}

/** 替换场景视频（步骤8） */
export function replaceVideo(taskId: string, videoId: string | number, newVideoUrl: string, newCoverUrl?: string) {
  return request.put('/api/workflow/pipeline/artifacts/video', { taskId, videoId, newVideoUrl, newCoverUrl })
}
