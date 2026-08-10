import request from './request'
import type { PageResult } from './task'

export interface AdminUserVO {
  id: number
  username: string
  nickname?: string
  avatar?: string
  email?: string
  phone?: string
  gender?: number
  status?: number
  roleNames?: string[]
  createTime?: string
}

export interface AdminUserPageQuery {
  page?: number
  size?: number
  keyword?: string
  status?: number
  role?: string
}

export interface AdminUserCreateDTO {
  username: string
  password: string
  nickname?: string
  email?: string
  phone?: string
  gender?: number
  roleNames?: string[]
}

export interface AdminUserUpdateDTO {
  id?: number
  nickname?: string
  email?: string
  phone?: string
  gender?: number
  roleNames?: string[]
  status?: number
}

export interface SystemConfigVO {
  id?: number
  configKey: string
  configValue: string
  valueType?: number
  configName?: string
  description?: string
  isSystem?: number
  status?: number
  createTime?: string
  updateTime?: string
}

export interface SystemStatsVO {
  userTotal: number
  userActive: number
  taskTotal: number
  taskRunning: number
  taskDone: number
  taskFailed: number
  workTotal: number
  todayNewUsers: number
  todayNewTasks: number
}

export interface OperationLogVO {
  id: number
  userId?: number
  username?: string
  operationType: string
  operationDesc?: string
  ip?: string
  duration?: number
  status?: number
  createTime?: string
}

export interface OperationLogPageQuery {
  page?: number
  size?: number
  module?: string
  username?: string
}

export function pageUsers(params: AdminUserPageQuery) {
  return request.get<any, PageResult<AdminUserVO>>('/api/sys/user/page', { params })
}

export function getUser(id: number) {
  return request.get<any, AdminUserVO>(`/api/sys/user/${id}`)
}

export function createUser(data: AdminUserCreateDTO) {
  return request.post<any, number>('/api/sys/user', data)
}

export function updateUser(id: number, data: AdminUserUpdateDTO) {
  return request.put<any, void>('/api/sys/user', { id, ...data })
}

export function deleteUser(id: number) {
  return request.delete(`/api/sys/user/${id}`)
}

export function enableUser(id: number) {
  return updateUser(id, { status: 1 })
}

export function disableUser(id: number) {
  return updateUser(id, { status: 0 })
}

export function resetUserPassword(id: number, newPassword: string) {
  return request.put<any, void>(`/api/sys/user/${id}/password`, null, { params: { newPassword } })
}

export function grantUserRoles(id: number, roleIds: number[]) {
  return request.put<any, void>(`/api/sys/user/${id}/roles`, roleIds)
}

export function getSystemConfigs() {
  return request.get<any, SystemConfigVO[]>('/api/system/config/list')
}

export function updateSystemConfig(data: SystemConfigVO) {
  return request.put<any, void>('/api/system/config', data)
}

export function getSystemStats() {
  return request.get<any, SystemStatsVO>('/api/admin/stats')
}

export function pageOperationLogs(params: OperationLogPageQuery) {
  return request.get<any, PageResult<OperationLogVO>>('/api/operation-log/page', { params })
}

// ===== 模型配置 =====
export interface AiModelConfigVO {
  id?: number
  modelProvider: string
  modelName: string
  modelType: number
  /** 调用协议：openai-chat/modelscope-image/ark-image/ark-tts/ark-video/custom-http-* */
  protocol?: string
  /** 能力声明 JSON 字符串，如 ["STREAMING","IMAGE_TO_IMAGE"] */
  capabilities?: string
  /** 负载均衡策略：WEIGHTED_RANDOM/ROUND_ROBIN/LOWEST_COST/FASTEST_RESPONSE */
  selectorStrategy?: string
  apiUrl: string
  apiKey?: string
  secretKey?: string
  status?: number
  /** 权重（多模型负载均衡，值越大调度概率越高，默认100） */
  weight?: number
  createTime?: string
  updateTime?: string
}

export interface AiModelConfigPageQuery {
  page?: number
  size?: number
  keyword?: string
  modelType?: number
  status?: number
}

/** 调用协议 VO（后端 ModelProtocol 枚举序列化） */
export interface ModelProtocolVO {
  code: string
  desc: string
  supportedTypes: number[]
  description: string
}

/** 能力声明 VO（后端 ModelCapability 枚举序列化） */
export interface ModelCapabilityVO {
  desc: string
  description: string
}

/** 负载均衡策略 VO（后端 SelectorStrategy 枚举序列化） */
export interface SelectorStrategyVO {
  code: string
  desc: string
  description: string
}

/** 连通性测试结果 VO（后端 AiModelTestResultDTO 序列化） */
export interface AiModelTestResultVO {
  success: boolean
  statusCode?: number
  latencyMs?: number
  message: string
}

export function pageModels(params: AiModelConfigPageQuery) {
  return request.get<any, PageResult<AiModelConfigVO>>('/api/system/model/page', { params })
}

export function getModel(id: number) {
  return request.get<any, AiModelConfigVO>(`/api/system/model/${id}`)
}

export function createModel(data: Partial<AiModelConfigVO>) {
  return request.post<any, number>('/api/system/model', data)
}

export function updateModel(id: number, data: Partial<AiModelConfigVO>) {
  return request.put<any, void>('/api/system/model', { id, ...data })
}

export function deleteModel(id: number) {
  return request.delete(`/api/system/model/${id}`)
}

/** 连通性测试：按模型配置的协议发起最小化探测请求 */
export function testModel(id: string | number) {
  return request.post<any, AiModelTestResultVO>(`/api/system/model/${id}/test`)
}

/** 返回所有可选调用协议 */
export function listProtocols() {
  return request.get<any, ModelProtocolVO[]>('/api/system/model/protocols')
}

/** 返回所有可选能力声明 */
export function listCapabilities() {
  return request.get<any, ModelCapabilityVO[]>('/api/system/model/capabilities')
}

/** 返回所有可选负载均衡策略 */
export function listSelectorStrategies() {
  return request.get<any, SelectorStrategyVO[]>('/api/system/model/selector-strategies')
}

// ===== 提示词模板 =====
export interface PromptTemplateVO {
  id?: number
  templateCode: string
  templateName: string
  stage: number
  content: string
  variables?: string
  description?: string
  currentVersion?: number
  isEnabled?: number
  createBy?: number
  createTime?: string
  updateTime?: string
}

export interface PromptTemplatePageQuery {
  page?: number
  size?: number
  keyword?: string
  stage?: number
}

export interface PromptTemplateVersionVO {
  id?: number
  templateId: number
  versionNo: number
  content: string
  variables?: string
  changeLog?: string
  isCurrent?: number
  createdBy?: number
  createTime?: string
}

export function pageTemplates(params: PromptTemplatePageQuery) {
  return request.get<any, PageResult<PromptTemplateVO>>('/api/template/page', { params })
}

export function getTemplate(id: number) {
  return request.get<any, PromptTemplateVO>(`/api/template/${id}`)
}

export function createTemplate(data: Partial<PromptTemplateVO>) {
  return request.post<any, number>('/api/template', data)
}

export function updateTemplate(id: number, data: Partial<PromptTemplateVO>) {
  return request.put<any, void>('/api/template', { id, ...data })
}

export function deleteTemplate(id: number) {
  return request.delete(`/api/template/${id}`)
}

export function listTemplateVersions(templateId: number) {
  return request.get<any, PromptTemplateVersionVO[]>(`/api/template/${templateId}/version`)
}

export function rollbackTemplate(templateId: number, versionNo: number) {
  return request.put<any, void>(`/api/template/${templateId}/rollback/${versionNo}`)
}

// ===== Token用量日志 =====
export interface TokenUsageLogVO {
  id?: number
  taskId?: number
  userId?: number
  step?: number
  nodeType?: string
  modelName: string
  modelType?: number
  promptTokens?: number
  completionTokens?: number
  totalTokens?: number
  imageCount?: number
  videoDuration?: number
  costAmount?: number
  latencyMs?: number
  status?: number
  errorMsg?: string
  createTime?: string
}

export interface TokenUsageLogPageQuery {
  page?: number
  size?: number
  keyword?: string
  modelName?: string
  modelType?: number
}

export function pageUsageLogs(params: TokenUsageLogPageQuery) {
  return request.get<any, PageResult<TokenUsageLogVO>>('/api/admin/token-usage/page', { params })
}

// ===== 系统配置 =====
export function pageConfigs(params: { page?: number; size?: number; keyword?: string }) {
  return request.get<any, PageResult<any>>('/api/system/config/page', { params })
}

export function createConfig(data: any) {
  return request.post<any, number>('/api/system/config', data)
}

export function updateConfig(id: number, data: any) {
  return request.put<any, void>('/api/system/config', { id, ...data })
}

export function deleteConfig(id: number) {
  return request.delete(`/api/system/config/${id}`)
}

// ===== 步骤-模型绑定 =====
export function listBindings() {
  return request.get<any, any[]>('/api/admin/binding/list')
}

export function createBinding(data: any) {
  return request.post<any, void>('/api/admin/binding', data)
}

export function updateBinding(id: number, data: any) {
  return request.put<any, void>(`/api/admin/binding/${id}`, data)
}

export function clearBinding(id: number) {
  return request.put<any, void>(`/api/admin/binding/${id}/clear`)
}

export function listActiveModels() {
  return request.get<any, any[]>('/api/admin/model/active-list')
}

export function listAllModels() {
  return request.get<any, any[]>('/api/admin/model/all-list')
}

// ===== 统计 =====
export function getAdminStats() {
  return request.get<any, Record<string, any>>('/api/admin/stats')
}

// ---- Token用量日志 ----
export const pageTokenUsage = (params: any) => request.get('/api/admin/token-usage/page', { params })
export const getTokenUsage = (id: number) => request.get(`/api/admin/token-usage/${id}`)
export const aggregateTokenUsage = (params: any) => request.get('/api/admin/token-usage/aggregate', { params })
export const deleteTokenUsage = (id: number) => request.delete(`/api/admin/token-usage/${id}`)

// ---- 步骤-模型绑定 ----
export const listStepBindings = () => request.get('/api/admin/step-binding/list')
export const getStepBinding = (id: number) => request.get(`/api/admin/step-binding/${id}`)
export const updateStepBinding = (id: number, data: any) => request.put(`/api/admin/step-binding/${id}`, data)
export const batchUpdateStepBindings = (data: any) => request.put('/api/admin/step-binding/batch', data)

// ---- 角色管理 ----
export const listRoles = (params: any) => request.get('/api/sys/role/list', { params })
export const getRole = (id: number) => request.get(`/api/sys/role/${id}`)
export const createRole = (data: any) => request.post('/api/sys/role', data)
export const updateRole = (data: any) => request.put('/api/sys/role', data)
export const deleteRole = (id: number) => request.delete(`/api/sys/role/${id}`)
export const listRolePermissions = (id: number) => request.get(`/api/sys/role/${id}/permissions`)
export const updateRolePermissions = (id: number, permissionIds: number[]) => request.put(`/api/sys/role/${id}/permissions`, permissionIds)

// ---- 权限管理 ----
export const listPermissions = (params: any) => request.get('/api/sys/permission/list', { params })
export const getPermission = (id: number) => request.get(`/api/sys/permission/${id}`)
export const createPermission = (data: any) => request.post('/api/sys/permission', data)
export const updatePermission = (data: any) => request.put('/api/sys/permission', data)
export const deletePermission = (id: number) => request.delete(`/api/sys/permission/${id}`)