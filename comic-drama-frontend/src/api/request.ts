import axios, { type AxiosInstance, type InternalAxiosRequestConfig, type AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'

/** 后端统一响应结构 */
export interface ApiResult<T = any> {
  code: number
  msg: string
  data: T
  timestamp: number
}

const service: AxiosInstance = axios.create({
  baseURL: '/',
  timeout: 30000
})

// 请求拦截：注入 Authorization
service.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = localStorage.getItem('cd_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截：统一处理业务码
service.interceptors.response.use(
  (response: AxiosResponse<ApiResult>) => {
    const res = response.data
    // 文件流等非标准响应直接放行
    if (res instanceof Blob || typeof res !== 'object' || res.code === undefined) {
      return response.data as any
    }
    if (res.code === 200) {
      return res.data
    }
    // 401/403 由拦截器统一处理，避免重复提示
    if (res.code === 401) {
      handleUnauthorized()
      ElMessage.error(res.msg || '登录已过期')
      return Promise.reject(new Error(res.msg || '登录已过期'))
    }
    // 其他业务错误：抛出 Error 给调用方自行决定是否提示
    const errMsg = (res && res.msg) ? res.msg : '请求失败'
    return Promise.reject(new Error(errMsg))
  },
  (error) => {
    const status = error?.response?.status
    const errMsg = error?.message || '网络请求失败'
    const code = error?.code
    // 允许调用方通过 header: X-Silent-Error=1 静默失败（不弹全局 toast），播放器加载 manifest 等场景使用
    const silent = error?.config?.headers &&
      (error.config.headers['X-Silent-Error'] === '1' ||
        (error.config.headers as any)['x-silent-error'] === '1')
    if (!silent) {
      // Vite dev proxy 无法连接后端时，返回一个 500/502 响应且消息里常带 ECONNREFUSED
      const proxyRefused = status >= 500 &&
        (String(errMsg).includes('ECONNREFUSED') ||
          (typeof error?.response?.data === 'string' && error.response.data.includes('ECONNREFUSED')))
      if (code === 'ECONNREFUSED' || proxyRefused) {
        ElMessage.error('后端服务尚未就绪，请稍等几秒或确认网关/微服务是否启动')
      } else if (status === 401) {
        handleUnauthorized()
        ElMessage.error('登录已过期，请重新登录')
      } else if (status === 403) {
        ElMessage.error('无操作权限')
      } else if (status === 404) {
        ElMessage.error('请求的资源不存在')
      } else if (status >= 500) {
        ElMessage.error('服务器错误，请稍后重试')
      } else if (error?.code === 'ECONNABORTED') {
        ElMessage.error('请求超时，请检查网络连接')
      } else if (!error?.response) {
        ElMessage.error('网络连接失败，请检查后端服务是否启动')
      }
    }
    // 其他错误由调用方决定是否提示
    return Promise.reject(new Error(errMsg))
  }
)

function handleUnauthorized() {
  localStorage.removeItem('cd_token')
  localStorage.removeItem('cd_user')
  ElMessage.warning('登录已过期，请重新登录')
  // 避免在登录页重复跳转
  if (!window.location.pathname.startsWith('/login')) {
    window.location.href = '/login'
  }
}

export default service
