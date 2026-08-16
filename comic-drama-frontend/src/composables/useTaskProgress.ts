import { ref, type Ref, onBeforeUnmount, watch } from 'vue'
import { getTaskProgress, type TaskProgressLogVO } from '@/api/task'
import { createTaskSocket, type ProgressPayload } from '@/utils/ws'
import { TaskStatus } from '@/constants/task'

export interface UseTaskProgressReturn {
  progressLogs: Ref<TaskProgressLogVO[]>
  currentStep: Ref<number>
  totalProgress: Ref<number>
  status: Ref<number>
  wsConnected: Ref<boolean>
  polling: Ref<boolean>
  /** 当前步骤已完成子项（批量步骤用） */
  itemDone: Ref<number | null>
  /** 当前步骤总子项（批量步骤用） */
  itemTotal: Ref<number | null>
  /** 当前步骤名（中文，优先取后端返回 stepName，无则本地 stepName() 回退） */
  stepName: Ref<string>
  /** 最后一次后端推送的精炼一句话文案（如 "3/8 已完成"） */
  lastMessage: Ref<string>
  startPolling: (interval?: number, forceDurationMs?: number) => void
  stopPolling: () => void
  refresh: () => Promise<void>
}

export interface UseTaskProgressOptions {
  autoStart?: boolean
  onStepCompleted?: (completedStep: number) => void
}

/**
 * 任务进度 composable：
 *   1. 先开轮询（兜底），同时尝试 WebSocket；
 *   2. 只有"真正收到过一次后端推送事件"后，才关闭轮询（避免连接建立了但事件链没打通，导致页面不刷新）；
 *   3. WS 断开或 5s 内无事件 → 自动回退到 2.5s 轮询；
 *   4. 任务到达终态（DONE / FAILED / PAUSED）自动停止；
 *   5. 步骤完成时触发 onStepCompleted 回调，供上层刷新详情数据。
 */
export function useTaskProgress(
  taskId: Ref<string | null>,
  options: UseTaskProgressOptions = {}
): UseTaskProgressReturn {
  const { autoStart = true, onStepCompleted } = options
  const progressLogs = ref<TaskProgressLogVO[]>([])
  const currentStep = ref(0)
  const totalProgress = ref(0)
  const status = ref(TaskStatus.QUEUE)
  const wsConnected = ref(false)
  const polling = ref(false)
  const itemDone = ref<number | null>(null)
  const itemTotal = ref<number | null>(null)
  const stepName = ref<string>('等待开始')
  const lastMessage = ref<string>('')

  let timer: ReturnType<typeof setInterval> | null = null
  let wsClient: ReturnType<typeof createTaskSocket> | null = null
  let lastReceivedAt = 0
  let wsWatchdog: ReturnType<typeof setTimeout> | null = null
  const lastCompletedStep = ref(0)

  /** WS 建立后 5s 内没收到真正 progress 事件 → 认为链路没打通，回退轮询 */
  function armWsWatchdog() {
    disarmWsWatchdog()
    wsWatchdog = setTimeout(() => {
      const alive = Date.now() - lastReceivedAt < 5000
      if (!alive && !shouldStop()) {
        console.warn('[task-progress] WS 连接建立但未收到事件，回退到轮询')
        wsConnected.value = false
        startPolling(2500)
      }
    }, 5500)
  }
  function disarmWsWatchdog() {
    if (wsWatchdog) {
      clearTimeout(wsWatchdog)
      wsWatchdog = null
    }
  }
  function markWsAlive() {
    lastReceivedAt = Date.now()
    // 真正收到过推送后，轮询可以关掉（直到 WS 再断开）
    if (polling.value) stopPolling()
  }

  function resolveStepName(s: number | undefined, fallback?: string): string {
    if (fallback && fallback.trim()) return fallback
    if (s == null || s <= 0) return '等待开始'
    // 动态 import 避免循环依赖
    return STEP_NAME_FALLBACK[s] ?? `步骤 ${s}`
  }
  const STEP_NAME_FALLBACK: Record<number, string> = {
    1: '故事摘要',
    2: '分镜脚本',
    3: '资产设计',
    4: '资产绘图',
    5: '衍生绘图',
    6: '分镜绘图',
    7: '配音合成',
    8: '视频生成',
    9: '视频合并'
  }

  function detectStepCompletion(newStep: number, newProgress: number) {
    if (newStep > lastCompletedStep.value) {
      for (let s = lastCompletedStep.value + 1; s < newStep; s++) {
        onStepCompleted?.(s)
      }
      lastCompletedStep.value = newStep
    } else if (newStep === lastCompletedStep.value && newProgress >= 100 && lastCompletedStep.value > 0) {
      onStepCompleted?.(newStep)
    }
  }

  function applyProgressLogs(logs: TaskProgressLogVO[]) {
    progressLogs.value = logs || []
    if (logs && logs.length > 0) {
      const last = logs[logs.length - 1]
      if (last.step) {
        const prevStep = currentStep.value
        currentStep.value = last.step
        stepName.value = resolveStepName(last.step, last.stepName)
        if (prevStep > 0 && last.step > prevStep) {
          for (let s = prevStep; s < last.step; s++) {
            onStepCompleted?.(s)
          }
        }
      }
      if (typeof last.progress === 'number') {
        totalProgress.value = last.progress
        if (last.step && last.progress >= 100) {
          detectStepCompletion(last.step, last.progress)
        }
      }
      // 轮询拿到的日志也要同步 itemDone/itemTotal（关键修复：否则只靠 WS 会导致轮询回退
      // 模式下批量进度数字不更新，页面看似"没刷新"）
      if (typeof (last as any).itemDone === 'number') itemDone.value = (last as any).itemDone
      if (typeof (last as any).itemTotal === 'number') itemTotal.value = (last as any).itemTotal
      if (typeof last.status === 'number') status.value = last.status
      if (last.message) lastMessage.value = last.message
    }
  }

  async function refresh() {
    const id = taskId.value
    if (!id) return
    try {
      const logs = await getTaskProgress(id)
      applyProgressLogs(logs || [])
    } catch {
      /* 忽略：降级通道下的网络抖动不处理 */
    }
  }

  function stopPolling() {
    if (timer) {
      clearInterval(timer)
      timer = null
    }
    polling.value = false
  }

  function shouldStop(): boolean {
    return [TaskStatus.DONE, TaskStatus.FAILED, TaskStatus.PAUSED].includes(status.value)
  }

  function startPolling(interval = 2000, forceDurationMs = 0) {
    stopPolling()
    polling.value = true
    const forceUntil = Date.now() + forceDurationMs
    // 立即拉一次
    refresh()
    timer = setInterval(() => {
      refresh()
      if (shouldStop() && Date.now() >= forceUntil) stopPolling()
    }, interval)
  }

  function startWebSocket() {
    const id = taskId.value
    if (!id) return
    try {
      wsClient = createTaskSocket(id, {
        onOpen: () => {
          wsConnected.value = true
          armWsWatchdog()
          // 首次连成功先主动拉一次，确保进入页面就有真实数据
          refresh()
        },
        onClose: () => {
          disarmWsWatchdog()
          wsConnected.value = false
          // WS 断开时回退到轮询
          if (!shouldStop()) startPolling(2500)
        },
        onProgress: (data: ProgressPayload) => {
          markWsAlive()
          if (typeof data.step === 'number') {
            const prevStep = currentStep.value
            currentStep.value = data.step
            stepName.value = resolveStepName(data.step, data.stepName)
            if (prevStep > 0 && data.step > prevStep) {
              for (let s = prevStep; s < data.step; s++) {
                onStepCompleted?.(s)
              }
            }
          }
          if (typeof data.itemDone === 'number') itemDone.value = data.itemDone
          if (typeof data.itemTotal === 'number') itemTotal.value = data.itemTotal
          if (typeof data.totalProgress === 'number') {
            totalProgress.value = data.totalProgress
            if (data.step && data.totalProgress >= 100) {
              detectStepCompletion(data.step, data.totalProgress)
            }
          } else if (typeof data.progress === 'number') {
            totalProgress.value = data.progress
            if (data.step && data.progress >= 100) {
              detectStepCompletion(data.step, data.progress)
            }
          }
          if (typeof data.status === 'number') status.value = data.status
          if (data.message) {
            lastMessage.value = data.message
            // 去重键：step + message + timestamp 分桶（同一秒内同步骤同消息视为一条）
            const dedupeKey = `${data.step ?? currentStep.value}-${data.message}-${Math.floor((data.timestamp ?? Date.now()) / 1000)}`
            const lastKey = progressLogs.value.length > 0
              ? `${progressLogs.value[progressLogs.value.length - 1].step}-${progressLogs.value[progressLogs.value.length - 1].message}-${Math.floor(new Date(progressLogs.value[progressLogs.value.length - 1].createTime || 0).getTime() / 1000)}`
              : ''
            if (dedupeKey !== lastKey) {
              progressLogs.value = [
                ...progressLogs.value.slice(-199), // 保留最新 200 条，防内存无限增长
                {
                  taskId: id,
                  step: data.step ?? currentStep.value,
                  stepName: data.stepName ?? stepName.value,
                  progress: data.totalProgress ?? data.progress ?? totalProgress.value,
                  status: data.status,
                  message: data.message,
                  createTime: data.timestamp ? new Date(data.timestamp).toISOString() : new Date().toISOString()
                }
              ]
            }
          }
          if (shouldStop() && wsClient) {
            wsClient.close()
            wsClient = null
          }
        }
      })
    } catch {
      wsConnected.value = false
      startPolling()
    }
  }

  // 监听 taskId 变化：重启全部通道
  watch(
    taskId,
    (newId, oldId) => {
      if (newId === oldId) return
      // 清理旧连接
      disarmWsWatchdog()
      if (wsClient) {
        wsClient.close()
        wsClient = null
      }
      stopPolling()
      progressLogs.value = []
      currentStep.value = 0
      totalProgress.value = 0
      status.value = TaskStatus.QUEUE
      wsConnected.value = false
      lastCompletedStep.value = 0
      itemDone.value = null
      itemTotal.value = null
      stepName.value = '等待开始'
      lastMessage.value = ''

      if (newId != null && newId !== '' && autoStart) {
        init(newId)
      }
    },
    { immediate: false }
  )

  // 任务进入终态（PAUSED/DONE/FAILED）或步骤变化时刷新详情：
  // 1) 人工审核模式下步骤完成会暂停（totalProgress 未到 100% 且 step 不递增，
  //    detectStepCompletion 不会触发），需在此刷新以更新 pendingReview，显示审核横幅
  // 2) 步骤递进时也需刷新以更新 nodeStates（步骤状态标签如"进行中"/"批量中"等依赖它）
  watch(status, (newStatus, oldStatus) => {
    if (newStatus === oldStatus) return
    if ([TaskStatus.DONE, TaskStatus.FAILED, TaskStatus.PAUSED].includes(newStatus)) {
      onStepCompleted?.(currentStep.value)
    }
  })
  watch(currentStep, (newStep, oldStep) => {
    if (newStep === oldStep || newStep <= 0) return
    onStepCompleted?.(newStep)
  })

  function init(_id: string) {
    // 先开轮询做兜底（WS 证实收到事件后自动关掉）
    startPolling(2000)
    startWebSocket()
  }

  // 自动启动（仅当 taskId 已有值）
  if (autoStart && taskId.value != null && taskId.value !== '') {
    init(taskId.value)
  }

  onBeforeUnmount(() => {
    disarmWsWatchdog()
    if (wsClient) {
      wsClient.close()
      wsClient = null
    }
    stopPolling()
  })

  return {
    progressLogs: progressLogs as Ref<TaskProgressLogVO[]>,
    currentStep,
    totalProgress,
    status,
    wsConnected,
    polling,
    itemDone,
    itemTotal,
    stepName,
    lastMessage,
    startPolling,
    stopPolling,
    refresh
  }
}
