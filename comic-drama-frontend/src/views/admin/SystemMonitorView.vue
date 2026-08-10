<template>
  <div class="system-monitor">
    <el-tabs v-model="activeSubTab" class="sub-tabs" @tab-change="handleTabChange">
      <!-- ==================== 任务队列 ==================== -->
      <el-tab-pane name="queue">
        <template #label>
          <span class="tab-label">
            任务队列
            <span v-if="queueTotal > 0" class="tab-badge">{{ queueTotal > 99 ? '99+' : queueTotal }}</span>
          </span>
        </template>

        <div class="filter-bar sketch-card">
          <div class="filter-left">
            <el-select
              v-model="queueQuery.status"
              placeholder="全部状态"
              clearable
              class="filter-select"
              @change="loadQueue"
            >
              <el-option v-for="s in queueStatusOptions" :key="s.value" :label="s.label" :value="s.value" />
            </el-select>
            <el-select
              v-model="queueQuery.queueName"
              placeholder="全部队列"
              clearable
              class="filter-select"
              @change="loadQueue"
            >
              <el-option label="AI 处理队列" value="AI_QUEUE" />
              <el-option label="视频渲染队列" value="VIDEO_QUEUE" />
              <el-option label="默认队列" value="DEFAULT" />
            </el-select>
            <el-select
              v-model="queueQuery.priority"
              placeholder="全部优先级"
              clearable
              class="filter-select"
              @change="loadQueue"
            >
              <el-option label="高优先级" :value="3" />
              <el-option label="中优先级" :value="2" />
              <el-option label="低优先级" :value="1" />
            </el-select>
          </div>
          <div class="filter-right">
            <button class="sketch-btn sketch-btn--ghost" :disabled="queueLoading" @click="loadQueue">
              刷新
            </button>
          </div>
        </div>

        <div class="table-card sketch-card" v-loading="queueLoading">
          <el-table
            :data="queueList"
            style="width: 100%"
            row-key="id"
            stripe
            empty-text="暂无队列任务"
            :header-cell-style="{ background: 'var(--cd-bg-soft)', color: 'var(--cd-text-secondary)', fontWeight: 600 }"
          >
            <el-table-column label="ID" width="80" align="center">
              <template #default="{ row }">
                <span class="mono">#{{ row.id }}</span>
              </template>
            </el-table-column>
            <el-table-column label="任务" min-width="180">
              <template #default="{ row }">
                <div class="task-cell">
                  <div class="task-id">关联 #{{ row.taskId }}</div>
                  <div class="queue-tag">{{ queueLabel(row.queueName) }}</div>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="110" align="center">
              <template #default="{ row }">
                <el-tag
                  :type="queueStatusTag(row.queueStatus)"
                  effect="light"
                  round
                  size="small"
                >
                  {{ queueStatusText(row.queueStatus) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="优先级" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="priorityTag(row.priority)" effect="plain" round size="small">
                  {{ priorityText(row.priority) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="排队位置" width="90" align="center">
              <template #default="{ row }">
                <span v-if="row.queuePosition != null && row.queuePosition > 0" class="queue-position">
                  第 {{ row.queuePosition }} 位
                </span>
                <span v-else class="text-secondary">-</span>
              </template>
            </el-table-column>
            <el-table-column label="等待时长" width="110" align="center">
              <template #default="{ row }">
                <span v-if="row.estimatedWaitSeconds != null && row.estimatedWaitSeconds > 0" class="wait-time">
                  {{ formatDuration(row.estimatedWaitSeconds) }}
                </span>
                <span v-else-if="row.queueStatus === 'processing'" class="processing-text">处理中</span>
                <span v-else class="text-secondary">-</span>
              </template>
            </el-table-column>
            <el-table-column label="重试" width="60" align="right">
              <template #default="{ row }">
                <span v-if="row.retryCount > 0" class="retry-count">{{ row.retryCount }}</span>
                <span v-else class="text-secondary">{{ row.retryCount ?? 0 }}</span>
              </template>
            </el-table-column>
            <el-table-column label="入队时间" width="130">
              <template #default="{ row }">{{ fmtTime(row.enqueuedTime || row.createTime) }}</template>
            </el-table-column>
            <el-table-column label="开始时间" width="130">
              <template #default="{ row }">{{ fmtTime(row.startedTime) }}</template>
            </el-table-column>
          </el-table>

          <div class="pagination">
            <el-pagination
              v-model:current-page="queueQuery.page"
              v-model:page-size="queueQuery.size"
              :total="queueTotal"
              :page-sizes="[20, 50, 100]"
              layout="total, sizes, prev, pager, next, jumper"
              background
              @current-change="loadQueue"
              @size-change="handleQueueSizeChange"
            />
          </div>
        </div>
      </el-tab-pane>

      <!-- ==================== 全局失败日志 ==================== -->
      <el-tab-pane name="failure">
        <template #label>
          <span class="tab-label">
            全局失败日志
            <span v-if="failureTotal > 0" class="tab-badge tab-badge--danger">{{ failureTotal > 99 ? '99+' : failureTotal }}</span>
          </span>
        </template>

        <div v-if="failureSummary.length > 0" class="summary-card sketch-card">
          <div class="summary-header">
            <h4>错误类型汇总</h4>
            <div class="summary-actions">
              <span class="summary-hint">共 {{ summaryTotal }} 条失败记录</span>
            </div>
          </div>
          <div class="summary-grid">
            <div v-for="item in failureSummary.slice(0, 5)" :key="item.errorType" class="summary-item">
              <div class="summary-item-count">{{ item.count }}</div>
              <div class="summary-item-label">{{ errorTypeText(item.errorType) }}</div>
              <div class="summary-item-bar">
                <div class="summary-item-bar-fill" :style="{ width: summaryTotal > 0 ? (item.count / summaryTotal * 100) + '%' : '0%' }"></div>
              </div>
              <div class="summary-item-percent">{{ summaryTotal > 0 ? ((item.count / summaryTotal) * 100).toFixed(1) : '0' }}%</div>
            </div>
            <div v-if="failureSummary.length > 5" class="summary-more">
              另有 {{ failureSummary.length - 5 }} 种错误类型，详见下方列表
            </div>
          </div>
        </div>

        <div class="filter-bar sketch-card" style="margin-top: 16px;">
          <div class="filter-left">
            <el-input
              v-model="failureQuery.errorType"
              placeholder="错误类型"
              :prefix-icon="Search"
              clearable
              class="search-input"
              @keyup.enter="loadFailures"
              @clear="loadFailures"
            />
            <el-input
              v-model="failureQuery.taskId"
              placeholder="任务 ID"
              clearable
              class="filter-input"
              @keyup.enter="loadFailures"
              @clear="loadFailures"
            />
            <el-select
              v-model="failureQuery.step"
              placeholder="步骤"
              clearable
              class="filter-select"
              @change="loadFailures"
            >
              <el-option v-for="i in 9" :key="i" :label="`步骤 ${i}`" :value="i" />
            </el-select>
            <el-select
              v-model="failureQuery.resolved"
              placeholder="解决状态"
              clearable
              class="filter-select"
              @change="loadFailures"
            >
              <el-option label="未解决" :value="0" />
              <el-option label="已解决" :value="1" />
            </el-select>
          </div>
          <div class="filter-right">
            <button class="sketch-btn sketch-btn--ghost" :disabled="failureLoading" @click="handleFailureSearch">
              搜索
            </button>
          </div>
        </div>

        <div class="table-card sketch-card" v-loading="failureLoading">
          <el-table
            :data="failureList"
            style="width: 100%"
            row-key="id"
            stripe
            empty-text="暂无失败记录"
            :header-cell-style="{ background: 'var(--cd-bg-soft)', color: 'var(--cd-text-secondary)', fontWeight: 600 }"
          >
            <el-table-column label="ID" width="70" align="center">
              <template #default="{ row }"><span class="mono">#{{ row.id }}</span></template>
            </el-table-column>
            <el-table-column label="任务" width="100" align="center">
              <template #default="{ row }"><span class="mono">{{ row.taskId }}</span></template>
            </el-table-column>
            <el-table-column label="步骤" width="70" align="center">
              <template #default="{ row }">
                <el-tag size="small" effect="plain" round type="warning">步骤 {{ row.step }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="错误类型" width="140" show-overflow-tooltip>
              <template #default="{ row }">{{ errorTypeText(row.errorType) }}</template>
            </el-table-column>
            <el-table-column label="错误码" width="100" align="center">
              <template #default="{ row }">
                <span v-if="row.errorCode" class="error-code">{{ row.errorCode }}</span>
                <span v-else class="text-secondary">-</span>
              </template>
            </el-table-column>
            <el-table-column label="模型" width="130" show-overflow-tooltip>
              <template #default="{ row }">{{ row.modelName || '-' }}</template>
            </el-table-column>
            <el-table-column label="错误信息" min-width="280" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="error-message">{{ row.errorMessage || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="重试" width="60" align="right">
              <template #default="{ row }">
                <span v-if="row.retryCount > 0" class="retry-count">{{ row.retryCount }}</span>
                <span v-else class="text-secondary">0</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="80" align="center">
              <template #default="{ row }">
                <el-tag
                  :type="row.resolved === 1 ? 'success' : 'danger'"
                  effect="plain"
                  round
                  size="small"
                >
                  {{ row.resolved === 1 ? '已解决' : '未解决' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="时间" width="130">
              <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
            </el-table-column>
          </el-table>

          <div class="pagination">
            <el-pagination
              v-model:current-page="failureQuery.page"
              v-model:page-size="failureQuery.size"
              :total="failureTotal"
              :page-sizes="[20, 50, 100]"
              layout="total, sizes, prev, pager, next, jumper"
              background
              @current-change="loadFailures"
              @size-change="handleFailureSizeChange"
            />
          </div>
        </div>
      </el-tab-pane>

      <!-- ==================== 统计日报 ==================== -->
      <el-tab-pane name="daily">
        <div class="filter-bar sketch-card">
          <div class="filter-left">
            <el-date-picker
              v-model="dailyQuery.date"
              type="date"
              placeholder="选择日期"
              class="date-picker"
              value-format="YYYY-MM-DD"
              :clearable="true"
              @change="loadDaily"
            />
            <el-input
              v-model="dailyQuery.keyword"
              placeholder="搜索日期 (YYYY-MM-DD)"
              :prefix-icon="Search"
              clearable
              class="search-input"
              @keyup.enter="loadDaily"
              @clear="loadDaily"
            />
          </div>
          <div class="filter-right">
            <button class="sketch-btn sketch-btn--ghost" :disabled="dailyLoading" @click="loadDaily">
              刷新
            </button>
          </div>
        </div>

        <!-- 核心指标卡片 -->
        <div class="daily-stats-grid">
          <div class="stat-card sketch-card">
            <div class="stat-icon stat-icon--users">👥</div>
            <div class="stat-content">
              <div class="stat-value">{{ dailySummary.newUserCount ?? 0 }}</div>
              <div class="stat-label">新增用户</div>
            </div>
          </div>
          <div class="stat-card sketch-card">
            <div class="stat-icon stat-icon--task">📋</div>
            <div class="stat-content">
              <div class="stat-value">{{ dailySummary.totalTaskCount ?? 0 }}</div>
              <div class="stat-label">总任务数</div>
            </div>
          </div>
          <div class="stat-card sketch-card">
            <div class="stat-icon stat-icon--done">✅</div>
            <div class="stat-content">
              <div class="stat-value">{{ dailySummary.successCount ?? 0 }}</div>
              <div class="stat-label">成功任务</div>
            </div>
          </div>
          <div class="stat-card sketch-card">
            <div class="stat-icon stat-icon--fail">⚠️</div>
            <div class="stat-content">
              <div class="stat-value stat-value--danger">{{ dailySummary.failureCount ?? 0 }}</div>
              <div class="stat-label">失败任务</div>
            </div>
          </div>
          <div class="stat-card sketch-card">
            <div class="stat-icon stat-icon--time">⏱️</div>
            <div class="stat-content">
              <div class="stat-value">{{ dailySummary.avgTotalTime ?? 0 }}s</div>
              <div class="stat-label">平均耗时</div>
            </div>
          </div>
          <div class="stat-card sketch-card">
            <div class="stat-icon stat-icon--disk">�</div>
            <div class="stat-content">
              <div class="stat-value">{{ formatNumber(latestDaily.diskUsageBytes) }}</div>
              <div class="stat-label">磁盘使用(B)</div>
            </div>
          </div>
        </div>

        <div class="table-card sketch-card" v-loading="dailyLoading">
          <el-table
            :data="dailyList"
            style="width: 100%"
            row-key="id"
            stripe
            empty-text="暂无统计数据"
            :header-cell-style="{ background: 'var(--cd-bg-soft)', color: 'var(--cd-text-secondary)', fontWeight: 600 }"
          >
            <el-table-column label="统计日期" width="120" align="center" prop="statDate">
              <template #default="{ row }">
                <span class="mono">{{ row.statDate }}</span>
              </template>
            </el-table-column>
            <el-table-column label="总任务" width="100" align="right">
              <template #default="{ row }">{{ row.totalTaskCount ?? 0 }}</template>
            </el-table-column>
            <el-table-column label="成功" width="90" align="right">
              <template #default="{ row }">{{ row.successCount ?? 0 }}</template>
            </el-table-column>
            <el-table-column label="失败" width="90" align="right">
              <template #default="{ row }">
                <span :class="{ danger: (row.failureCount ?? 0) > 0 }">{{ row.failureCount ?? 0 }}</span>
              </template>
            </el-table-column>
            <el-table-column label="成功率" width="100" align="right">
              <template #default="{ row }">
                <span>{{ row.successRate != null ? row.successRate + '%' : '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="新增用户" width="100" align="right">
              <template #default="{ row }">{{ row.newUserCount ?? 0 }}</template>
            </el-table-column>
            <el-table-column label="活跃用户" width="100" align="right">
              <template #default="{ row }">{{ row.activeUserCount ?? 0 }}</template>
            </el-table-column>
            <el-table-column label="平均耗时(s)" width="110" align="right">
              <template #default="{ row }">{{ row.avgTotalTime ?? 0 }}</template>
            </el-table-column>
            <el-table-column label="磁盘使用" width="120" align="right">
              <template #default="{ row }">{{ formatNumber(row.diskUsageBytes) }}</template>
            </el-table-column>
          </el-table>

          <div class="pagination">
            <el-pagination
              v-model:current-page="dailyQuery.page"
              v-model:page-size="dailyQuery.size"
              :total="dailyTotal"
              :page-sizes="[15, 30, 60]"
              layout="total, sizes, prev, pager, next, jumper"
              background
              @current-change="loadDaily"
              @size-change="handleDailySizeChange"
            />
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, computed } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { getQueuePage, getFailurePage, getDailyStatsPage } from '@/api/task'

// ==================== 全局 ====================
const activeSubTab = ref('queue')
const tabLoaded: Record<string, boolean> = { queue: false, failure: false, daily: false }

function handleTabChange(tab: string | number) {
  loadTabData(String(tab), false)
}

async function loadTabData(tab: string, force = false) {
  if (force || !tabLoaded[tab]) {
    switch (tab) {
      case 'queue':
        tabLoaded[tab] = true
        await loadQueue()
        break
      case 'failure':
        tabLoaded[tab] = true
        await Promise.all([loadFailures(), loadFailureSummary()])
        break
      case 'daily':
        tabLoaded[tab] = true
        await loadDaily()
        break
    }
  }
}

function fmtTime(t?: string) {
  if (!t) return '-'
  return t.replace('T', ' ').slice(0, 16)
}

function formatNumber(n: number | null | undefined): string {
  const v = n ?? 0
  if (Math.abs(v) >= 10000) {
    return (v / 10000).toFixed(1) + 'w'
  }
  return v.toLocaleString()
}

function formatDuration(seconds: number): string {
  if (seconds < 60) return seconds + 's'
  if (seconds < 3600) return Math.floor(seconds / 60) + 'm ' + (seconds % 60) + 's'
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  return h + 'h ' + m + 'm'
}

// ==================== 任务队列 ====================
// queueStatus: 0=等待中, 1=处理中, 2=已完成, 3=已取消
const queueStatusOptions = [
  { label: '等待中', value: 0 },
  { label: '处理中', value: 1 },
  { label: '已完成', value: 2 },
  { label: '已取消', value: 3 }
]

const queueStatusMap: Record<number, { text: string; tag: 'primary' | 'success' | 'warning' | 'info' | 'danger' }> = {
  0: { text: '等待中', tag: 'warning' },
  1: { text: '处理中', tag: 'primary' },
  2: { text: '已完成', tag: 'success' },
  3: { text: '已取消', tag: 'info' }
}

function queueStatusText(s?: number) {
  if (s === null || s === undefined) return '-'
  return queueStatusMap[s]?.text || String(s)
}

function queueStatusTag(s?: number) {
  if (s === null || s === undefined) return 'info' as const
  return queueStatusMap[s]?.tag || 'info' as const
}

function queueLabel(name?: string) {
  const map: Record<string, string> = {
    AI_QUEUE: 'AI 处理',
    VIDEO_QUEUE: '视频渲染',
    DEFAULT: '默认'
  }
  return name ? (map[name] || name) : '-'
}

function priorityText(p?: number) {
  if (p == null) return '-'
  return p >= 3 ? '高' : p >= 2 ? '中' : '低'
}

function priorityTag(p?: number) {
  if (p == null) return 'info' as const
  return p >= 3 ? 'danger' as const : p >= 2 ? 'warning' as const : 'info' as const
}

const queueLoading = ref(false)
const queueList = ref<any[]>([])
const queueTotal = ref(0)
const queueQuery = reactive({
  page: 1, size: 20,
  status: undefined as number | undefined,
  queueName: undefined as string | undefined,
  priority: undefined as number | undefined
})

async function loadQueue() {
  queueLoading.value = true
  try {
    const params: any = { page: queueQuery.page, size: queueQuery.size }
    if (queueQuery.status !== undefined && queueQuery.status !== null) params.queueStatus = queueQuery.status
    if (queueQuery.queueName) params.queueName = queueQuery.queueName
    const res: any = await getQueuePage(params)
    queueList.value = res.records || res || []
    queueTotal.value = res.total || queueList.value.length
  } catch (e) { /* 拦截器已提示 */ }
  finally { queueLoading.value = false }
}
function handleQueueSizeChange() { queueQuery.page = 1; loadQueue() }

// ==================== 全局失败日志 ====================
const failureLoading = ref(false)
const failureList = ref<any[]>([])
const failureTotal = ref(0)
const failureSummary = ref<{ errorType: string; count: number }[]>([])
const failureQuery = reactive({
  page: 1, size: 20,
  errorType: '' as string | undefined,
  taskId: '' as string | undefined,
  step: undefined as number | undefined,
  resolved: undefined as number | undefined
})

const summaryTotal = computed(() => {
  return failureSummary.value.reduce((acc, cur) => acc + (cur.count || 0), 0)
})

function errorTypeText(t?: string) {
  const map: Record<string, string> = {
    API_ERROR: 'API 调用错误',
    TIMEOUT: '请求超时',
    RATE_LIMITED: '速率限制',
    INVALID_PARAM: '参数错误',
    MODEL_NOT_FOUND: '模型不存在',
    STORAGE_ERROR: '存储错误',
    NETWORK_ERROR: '网络错误',
    UNKNOWN: '未知错误'
  }
  return t ? (map[t] || t) : '未知错误'
}

async function loadFailures() {
  failureLoading.value = true
  try {
    const params: any = { page: failureQuery.page, size: failureQuery.size }
    if (failureQuery.errorType) params.error_type = failureQuery.errorType
    if (failureQuery.taskId) params.taskId = failureQuery.taskId
    if (failureQuery.step) params.step = failureQuery.step
    if (failureQuery.resolved !== undefined) params.resolved = failureQuery.resolved
    const res: any = await getFailurePage(params)
    failureList.value = res.records || res || []
    failureTotal.value = res.total || failureList.value.length
  } catch (e) { /* 拦截器已提示 */ }
  finally { failureLoading.value = false }
}
function handleFailureSizeChange() { failureQuery.page = 1; loadFailures() }
function handleFailureSearch() { failureQuery.page = 1; loadFailures() }

async function loadFailureSummary() {
  try {
    const res: any = await getFailurePage({ page: 1, size: 9999 })
    const list: any[] = res.records || res || []
    const map: Record<string, number> = {}
    for (const item of list) {
      const key = item.errorType || item.error_type || 'UNKNOWN'
      map[key] = (map[key] || 0) + 1
    }
    failureSummary.value = Object.entries(map)
      .map(([errorType, count]) => ({ errorType, count }))
      .sort((a, b) => b.count - a.count)
  } catch (e) {
    failureSummary.value = []
  }
}

// ==================== 统计日报 ====================
const dailyLoading = ref(false)
const dailyList = ref<any[]>([])
const dailyTotal = ref(0)
const dailyQuery = reactive({ page: 1, size: 15, keyword: '' as string, date: '' as string })

// 最近一天数据的快捷指标（使用 TaskStatisticsDaily 实体字段）
const latestDaily = computed(() => {
  if (dailyList.value.length === 0) return {} as any
  return dailyList.value[0] || {}
})

const dailySummary = computed(() => {
  const d = latestDaily.value
  return {
    newUserCount: d.newUserCount ?? 0,
    totalTaskCount: d.totalTaskCount ?? 0,
    successCount: d.successCount ?? 0,
    failureCount: d.failureCount ?? 0,
    avgTotalTime: d.avgTotalTime ?? 0
  }
})

async function loadDaily() {
  dailyLoading.value = true
  try {
    const params: any = { page: dailyQuery.page, size: dailyQuery.size }
    if (dailyQuery.keyword) params.keyword = dailyQuery.keyword
    if (dailyQuery.date) params.date = dailyQuery.date
    const res: any = await getDailyStatsPage(params)
    dailyList.value = res.records || res || []
    dailyTotal.value = res.total || dailyList.value.length
  } catch (e) { /* 拦截器已提示 */ }
  finally { dailyLoading.value = false }
}
function handleDailySizeChange() { dailyQuery.page = 1; loadDaily() }

onMounted(() => {
  loadTabData(activeSubTab.value, false)
})
</script>

<style scoped>
.system-monitor {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.sub-tabs :deep(.el-tabs__header) {
  margin-bottom: 16px;
}

/* ===== Tab 标签 ===== */
.tab-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.tab-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 20px;
  height: 18px;
  padding: 0 6px;
  font-size: 11px;
  font-weight: 700;
  color: #fff;
  background: var(--cd-primary);
  border-radius: 9px;
}

.tab-badge--danger {
  background: var(--cd-danger, #f56c6c);
}

/* ===== 筛选栏 ===== */
.filter-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 18px;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}

.filter-left {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.filter-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.filter-select {
  width: 160px;
}

.filter-input {
  width: 140px;
}

.search-input {
  width: 220px;
}

.date-picker {
  width: 180px;
}

/* ===== 表格 ===== */
.table-card {
  padding: 16px 18px 18px;
}

.mono {
  font-family: 'SF Mono', 'Menlo', 'Consolas', monospace;
  font-size: 12px;
  color: var(--cd-text-secondary);
}

.text-secondary {
  color: var(--cd-text-secondary);
  font-size: 12px;
}

.task-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.task-id {
  font-size: 13px;
  font-weight: 600;
  color: var(--cd-text);
}

.queue-tag {
  display: inline-block;
  font-size: 11px;
  padding: 1px 8px;
  border-radius: 10px;
  background: var(--cd-bg-soft);
  color: var(--cd-text-secondary);
  width: fit-content;
}

.queue-position {
  font-weight: 600;
  color: var(--cd-text);
}

.wait-time {
  font-size: 12px;
  color: var(--cd-primary);
  font-weight: 600;
}

.processing-text {
  color: var(--cd-primary);
  font-weight: 500;
  font-size: 12px;
}

.retry-count {
  color: var(--cd-warning, #e6a23c);
  font-weight: 600;
}

.error-code {
  font-family: monospace;
  font-size: 12px;
  color: var(--cd-danger, #f56c6c);
}

.error-message {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  font-size: 12px;
  color: var(--cd-text);
  line-height: 1.5;
}

.danger {
  color: var(--cd-danger, #f56c6c);
  font-weight: 600;
}

/* ===== 分页 ===== */
.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

/* ===== 失败汇总卡片 ===== */
.summary-card {
  padding: 16px 18px;
}

.summary-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
}

.summary-header h4 {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
  color: var(--cd-text);
}

.summary-hint {
  font-size: 13px;
  color: var(--cd-text-secondary);
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 14px;
}

.summary-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  background: var(--cd-bg-soft);
  border-radius: 8px;
  transition: background 0.2s;
}

.summary-item:hover {
  background: var(--cd-bg-hover);
}

.summary-item-count {
  min-width: 36px;
  text-align: right;
  font-size: 18px;
  font-weight: 700;
  color: var(--cd-danger, #f56c6c);
}

.summary-item-label {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  color: var(--cd-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.summary-item-bar {
  flex: 1;
  height: 4px;
  background: var(--cd-border);
  border-radius: 2px;
  overflow: hidden;
  min-width: 60px;
}

.summary-item-bar-fill {
  height: 100%;
  background: var(--cd-danger, #f56c6c);
  border-radius: 2px;
  transition: width 0.3s;
}

.summary-item-percent {
  min-width: 50px;
  text-align: right;
  font-size: 12px;
  color: var(--cd-text-secondary);
}

.summary-more {
  grid-column: 1 / -1;
  font-size: 12px;
  color: var(--cd-text-secondary);
  text-align: center;
  padding-top: 4px;
}

/* ===== 统计日报 ===== */
.daily-stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 14px;
  margin-bottom: 16px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 20px;
  background: var(--cd-bg-card);
}

.stat-icon {
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  border-radius: 10px;
  background: var(--cd-bg-soft);
}

.stat-icon--users { background: rgba(64, 158, 255, 0.12); }
.stat-icon--task { background: rgba(144, 147, 153, 0.12); }
.stat-icon--done { background: rgba(103, 194, 58, 0.12); }
.stat-icon--fail { background: rgba(245, 108, 108, 0.12); }
.stat-icon--token { background: rgba(230, 162, 60, 0.12); }
.stat-icon--cost { background: rgba(126, 87, 194, 0.12); }

.stat-content {
  flex: 1;
  min-width: 0;
}

.stat-value {
  font-size: 22px;
  font-weight: 700;
  color: var(--cd-text);
  line-height: 1.2;
}

.stat-value--danger {
  color: var(--cd-danger, #f56c6c);
}

.stat-label {
  font-size: 13px;
  color: var(--cd-text-secondary);
  margin-top: 4px;
}
</style>
