<template>
  <div class="task-list">
    <!-- 筛选栏 -->
    <div class="filter-bar sketch-card">
      <div class="filter-left">
        <el-input
          v-model="query.keyword"
          placeholder="搜索任务编号 / 标题"
          :prefix-icon="Search"
          clearable
          class="search-input"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-select v-model="query.status" placeholder="全部状态" clearable class="status-select" @change="handleSearch">
          <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
        </el-select>
        <el-checkbox v-if="userStore.isAdmin" v-model="query.queryAll" @change="handleSearch">
          查看全部用户
        </el-checkbox>
      </div>
      <div class="filter-right">
        <Doodles :size="50" :rotate="-8" type="stars" :opacity="0.65" />
        <button class="sketch-btn" @click="router.push('/task/create')">
          <el-icon><Plus /></el-icon>
          新建任务
        </button>
      </div>
    </div>

    <!-- 任务表格 -->
    <div class="table-card sketch-card" v-loading="loading">
      <el-table :data="tableData" style="width: 100%" row-key="id" empty-text="暂无任务">
        <el-table-column label="任务" min-width="220">
          <template #default="{ row }">
            <div class="cell-task">
              <div class="cell-title-row">
                <span class="cell-title">{{ row.title || '未命名任务' }}</span>
                <el-tag
                  :type="row.execMode === 1 ? 'warning' : 'info'"
                  effect="light"
                  size="small"
                  class="cell-exec-tag"
                >
                  {{ row.execMode === 1 ? '人工审核' : '全自动' }}
                </el-tag>
              </div>
              <div class="cell-sub">{{ row.taskNo }}</div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="statusMeta(row.status).type" effect="light" round>
              {{ statusMeta(row.status).label }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="当前步骤" width="120" align="center">
          <template #default="{ row }">
            <span class="cell-step">{{ stepName(row.currentStep) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="进度" width="180">
          <template #default="{ row }">
            <el-progress
              :percentage="row.progress || 0"
              :stroke-width="8"
              :color="progressColor(row.status)"
              :status="progressStatus(row.status)"
            />
          </template>
        </el-table-column>

        <el-table-column label="排队位置" width="90" align="center">
          <template #default="{ row }">
            <span v-if="row.queuePosition && row.status === 0" class="cell-pos">#{{ row.queuePosition }}</span>
            <span v-else class="cell-dash">—</span>
          </template>
        </el-table-column>

        <el-table-column label="创建时间" width="160">
          <template #default="{ row }">
            <span class="cell-time">{{ fmtTime(row.createTime) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="goDetail(row as TaskItem)">详情</el-button>
            <el-button
              v-if="row.status === TaskStatus.QUEUE || row.status === TaskStatus.RUNNING"
              link
              type="warning"
              size="small"
              @click="handlePause(row as TaskItem)"
            >
              暂停
            </el-button>
            <el-button
              v-if="row.status === TaskStatus.PAUSED"
              link
              type="success"
              size="small"
              @click="handleResume(row as TaskItem)"
            >
              恢复
            </el-button>
            <el-button
              v-if="row.status === TaskStatus.FAILED"
              link
              type="primary"
              size="small"
              @click="handleRetry(row as TaskItem)"
            >
              重试
            </el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row as TaskItem)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="loadData"
          @size-change="handleSizeChange"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus } from '@element-plus/icons-vue'
import {
  pageTask,
  deleteTask,
  pauseTask,
  resumeTask,
  retryTask,
  type TaskItem
} from '@/api/task'
import { useUserStore } from '@/stores/user'
import { TaskStatus, statusMeta, stepName } from '@/constants/task'
import { Doodles } from '@/components/illustrations'

const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const tableData = ref<TaskItem[]>([])
const total = ref(0)

const query = reactive({
  page: 1,
  size: 10,
  keyword: '',
  status: undefined as number | undefined,
  queryAll: false
})

const statusOptions = [
  { label: '排队中', value: TaskStatus.QUEUE },
  { label: '生成中', value: TaskStatus.RUNNING },
  { label: '已完成', value: TaskStatus.DONE },
  { label: '失败', value: TaskStatus.FAILED },
  { label: '已暂停', value: TaskStatus.PAUSED }
]

function fmtTime(t?: string) {
  if (!t) return ''
  return t.replace('T', ' ').slice(0, 16)
}

function progressColor(status?: number) {
  return `var(${statusMeta(status).colorVar})`
}

function progressStatus(status?: number): '' | 'success' | 'exception' | 'warning' {
  if (status === TaskStatus.DONE) return 'success'
  if (status === TaskStatus.FAILED) return 'exception'
  if (status === TaskStatus.PAUSED) return 'warning'
  return ''
}

async function loadData() {
  loading.value = true
  try {
    const res = await pageTask(query)
    tableData.value = res.records || []
    total.value = res.total || 0
  } catch (e) {
    /* 拦截器提示 */
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.page = 1
  loadData()
}

function handleSizeChange() {
  query.page = 1
  loadData()
}

function goDetail(row: TaskItem) {
  router.push(`/task/${row.id}`)
}

async function handlePause(row: TaskItem) {
  await ElMessageBox.confirm(`确定暂停任务「${row.title || row.taskNo}」吗？`, '暂停任务', {
    type: 'warning'
  })
    .then(async () => {
      await pauseTask(row.id)
      ElMessage.success('任务已暂停')
      loadData()
    })
    .catch(() => {})
}

async function handleResume(row: TaskItem) {
  await resumeTask(row.id)
  ElMessage.success('任务已恢复，重新排队')
  loadData()
}

async function handleRetry(row: TaskItem) {
  await ElMessageBox.confirm(`确定重试任务「${row.title || row.taskNo}」吗？`, '重试任务', {
    type: 'info'
  })
    .then(async () => {
      await retryTask(row.id)
      ElMessage.success('任务已重新入队')
      loadData()
    })
    .catch(() => {})
}

async function handleDelete(row: TaskItem) {
  await ElMessageBox.confirm(`确定删除任务「${row.title || row.taskNo}」吗？此操作不可恢复。`, '删除任务', {
    type: 'error',
    confirmButtonText: '删除',
    confirmButtonClass: 'el-button--danger'
  })
    .then(async () => {
      await deleteTask(row.id)
      ElMessage.success('任务已删除')
      loadData()
    })
    .catch(() => {})
}

onMounted(loadData)
</script>

<style scoped>
.task-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.filter-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 18px;
  gap: 12px;
  flex-wrap: wrap;
}
.filter-left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.filter-right {
  display: flex;
  align-items: center;
  gap: 8px;
}
.search-input {
  width: 260px;
}
.status-select {
  width: 140px;
}

.table-card {
  padding: 8px 18px 18px;
  overflow: hidden;
}

.cell-task {
  display: flex;
  flex-direction: column;
}
.cell-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.cell-exec-tag {
  flex-shrink: 0;
}
.cell-title {
  font-weight: 600;
  color: var(--cd-text);
  font-size: 14px;
}
.cell-sub {
  font-size: 12px;
  color: var(--cd-text-secondary);
  margin-top: 2px;
}
.cell-step {
  font-size: 13px;
  color: var(--cd-text);
}
.cell-pos {
  font-weight: 700;
  color: var(--cd-warning);
}
.cell-dash {
  color: var(--cd-text-secondary);
}
.cell-time {
  font-size: 12px;
  color: var(--cd-text-secondary);
}

.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

@media (max-width: 768px) {
  .filter-bar {
    flex-direction: column;
    align-items: stretch;
  }
  .filter-left,
  .filter-right {
    flex-direction: column;
    align-items: stretch;
  }
  .search-input,
  .status-select {
    width: 100%;
  }
}
</style>
