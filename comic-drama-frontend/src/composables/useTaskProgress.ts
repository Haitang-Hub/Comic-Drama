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
 *   1. 优先使用 WebSocket 接收实时推送
 *   2. WebSocket 不可用时自动降级为 HTTP 定时轮询
 *   3. 任务到达终态（DONE / FAILED / PAUSED）时自动停止
 *   4. 步骤完成时触发 onStepCompleted 回调，供上层刷新详情数据
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

  let timer: ReturnType<typeof setInterval> | null = null
  let wsClient: ReturnType<typeof createTaskSocket> | null = null
  const lastCompletedStep = ref(0)

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
      if (typeof last.status === 'number') status.value = last.status
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
          // WS 连通后关闭轮询（保留一次主动刷新以对齐状态）
          stopPolling()
          refresh()
        },
        onClose: () => {
          wsConnected.value = false
          // WS 断开时回退到轮询
          if (!shouldStop()) startPolling(2500)
        },
        onProgress: (data: ProgressPayload) => {
          if (typeof data.step === 'number') {
            const prevStep = currentStep.value
            currentStep.value = data.step
            if (prevStep > 0 && data.step > prevStep) {
              for (let s = prevStep; s < data.step; s++) {
                onStepCompleted?.(s)
              }
            }
          }
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
            progressLogs.value = [
              ...progressLogs.value,
              {
                taskId: id,
                step: data.step ?? currentStep.value,
                stepName: data.stepName,
                progress: data.totalProgress ?? data.progress ?? totalProgress.value,
                status: data.status,
                message: data.message,
                createTime: data.timestamp ? new Date(data.timestamp).toISOString() : new Date().toISOString()
              }
            ]
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
    // 默认先开轮询，WS 连通后自动抢占
    startPolling()
    startWebSocket()
  }

  // 自动启动（仅当 taskId 已有值）
  if (autoStart && taskId.value != null && taskId.value !== '') {
    init(taskId.value)
  }

  onBeforeUnmount(() => {
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
    startPolling,
    stopPolling,
    refresh
  }
}
