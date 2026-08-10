/**
 * WebSocket 封装（Phase-4 实时进度推送预留）。
 *
 * Phase-1 后端尚未启用 WS 端点，本封装提供：
 *   - 自动重连（指数退避，上限 30s）
 *   - 心跳保活（30s ping）
 *   - 订阅/取消订阅 topic（按 taskId 推送进度）
 *   - 降级：连接失败时静默放弃，不影响主流程轮询
 *
 * 用法：
 *   const ws = createTaskSocket(taskId, {
 *     onProgress: (data) => { ... },
 *     onClose: () => { ... }
 *   })
 *   ws.close()
 */

export interface ProgressPayload {
  taskId: string
  step?: number
  stepName?: string
  progress: number
  totalProgress?: number
  status?: number
  message?: string
  timestamp?: number
}

export interface TaskSocketOptions {
  onProgress?: (data: ProgressPayload) => void
  onOpen?: () => void
  onClose?: () => void
  onError?: (e: Event) => void
}

const HEARTBEAT_INTERVAL = 30000
const MAX_RETRY_DELAY = 30000

function resolveWsUrl(taskId: string): string {
  // 默认走 vite dev server 同源，由 gateway 升级到 WS
  const proto = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${proto}//${window.location.host}/ws/task/${taskId}?token=${encodeURIComponent(
    localStorage.getItem('cd_token') || ''
  )}`
}

export function createTaskSocket(taskId: string, opts: TaskSocketOptions = {}) {
  let ws: WebSocket | null = null
  let retryCount = 0
  let manualClose = false
  let heartbeatTimer: ReturnType<typeof setInterval> | null = null

  const clearHeartbeat = () => {
    if (heartbeatTimer) {
      clearInterval(heartbeatTimer)
      heartbeatTimer = null
    }
  }

  const startHeartbeat = () => {
    clearHeartbeat()
    heartbeatTimer = setInterval(() => {
      if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({ type: 'ping' }))
      }
    }, HEARTBEAT_INTERVAL)
  }

  const connect = () => {
    try {
      ws = new WebSocket(resolveWsUrl(taskId))
    } catch (e) {
      // Phase-1 后端未就绪时静默失败
      return
    }

    ws.onopen = () => {
      retryCount = 0
      startHeartbeat()
      opts.onOpen?.()
    }

    ws.onmessage = (ev) => {
      try {
        const msg = JSON.parse(ev.data)
        if (msg.type === 'pong') return
        if (msg.type === 'progress' || msg.taskId != null) {
          // 后端封装格式：{ type: "progress", data: payload, timestamp }
          // 或直接发送 payload 对象
          const payload: ProgressPayload = msg.data || msg
          opts.onProgress?.(payload)
        }
      } catch {
        /* 忽略非 JSON 帧 */
      }
    }

    ws.onerror = (e) => {
      opts.onError?.(e)
    }

    ws.onclose = () => {
      clearHeartbeat()
      opts.onClose?.()
      if (!manualClose) {
        // 指数退避重连
        const delay = Math.min(1000 * Math.pow(2, retryCount), MAX_RETRY_DELAY)
        retryCount++
        setTimeout(connect, delay)
      }
    }
  }

  connect()

  return {
    close() {
      manualClose = true
      clearHeartbeat()
      if (ws) {
        ws.close()
        ws = null
      }
    },
    isOpen() {
      return ws?.readyState === WebSocket.OPEN
    }
  }
}
