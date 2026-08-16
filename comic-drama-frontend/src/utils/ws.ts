/**
 * 任务进度 WebSocket 封装。
 *
 *   - 自动重连（指数退避，上限 30s）
 *   - 心跳：服务端 30s 下发 ping，收到立即回 pong（客户端不主动 ping）
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
  /** 当前步骤已完成子项数（批量步骤） */
  itemDone?: number
  /** 当前步骤总子项数（批量步骤） */
  itemTotal?: number
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

  const connect = () => {
    try {
      ws = new WebSocket(resolveWsUrl(taskId))
    } catch (e) {
      // 后端未就绪时静默失败
      return
    }

    ws.onopen = () => {
      retryCount = 0
      opts.onOpen?.()
    }

    ws.onmessage = (ev) => {
      try {
        const msg = JSON.parse(ev.data)
        if (msg.type === 'ping') {
          // 服务端心跳 ping → 立即回 pong
          if (ws && ws.readyState === WebSocket.OPEN) {
            try { ws.send(JSON.stringify({ type: 'pong' })) } catch { /* ignore */ }
          }
          return
        }
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
