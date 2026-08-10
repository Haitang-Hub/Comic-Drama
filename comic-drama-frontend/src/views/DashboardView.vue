<template>
  <div class="dashboard">
    <!-- 欢迎区 -->
    <div class="welcome sketch-card">
      <div class="welcome-text">
        <h2>你好，{{ userStore.nickname || '创作者' }} 👋</h2>
        <p>输入一句话，让 AI 为你生成一部漫剧。9 步流水线，从大纲到成片。</p>
      </div>
      <div class="welcome-decor">
        <Pencil :size="90" :rotate="-15" variant="primary" />
        <Doodles :size="60" :rotate="10" type="sparkle" />
      </div>
      <button class="sketch-btn create-btn" @click="router.push('/task/create')">
        <el-icon><Plus /></el-icon>
        新建任务
      </button>
    </div>

    <!-- 统计卡片 -->
    <div class="stat-grid">
      <div v-for="s in stats" :key="s.key" class="stat-card sketch-card">
        <div class="stat-icon" :style="{ backgroundColor: s.color }">
          <el-icon><component :is="s.icon" /></el-icon>
        </div>
        <div class="stat-body">
          <div class="stat-value">{{ s.value }}</div>
          <div class="stat-label">{{ s.label }}</div>
        </div>
      </div>
    </div>

    <!-- 双栏：最近任务 + 流水线说明 -->
    <div class="dual-grid">
      <div class="panel sketch-card">
        <div class="panel-head">
          <h3>最近任务</h3>
          <a class="more" @click="router.push('/task')">查看全部 →</a>
        </div>
        <div v-loading="loading">
          <div v-if="recentTasks.length === 0 && !loading" class="empty">
            <StickyNote :size="120" :rotate="-5" color="soft" />
            <p>还没有任务，去创建第一个吧</p>
          </div>
          <div v-for="t in recentTasks" :key="t.id" class="task-row" @click="router.push('/task')">
            <div class="task-row-main">
              <div class="task-title">{{ t.title || t.taskNo }}</div>
              <div class="task-sub">{{ t.taskNo }} · {{ fmtTime(t.createTime) }}</div>
            </div>
            <el-progress
              :percentage="t.progress || 0"
              :stroke-width="6"
              :color="progressColor(t.status)"
              class="task-progress"
            />
            <el-tag :type="statusMeta(t.status).type" effect="light" round>
              {{ statusMeta(t.status).label }}
            </el-tag>
          </div>
        </div>
      </div>

      <div class="panel sketch-card">
        <div class="panel-head">
          <h3>9 步 AI 流水线</h3>
        </div>
        <div class="pipeline">
          <div v-for="(name, idx) in stepList" :key="idx" class="pipe-step">
            <div class="pipe-num">{{ idx + 1 }}</div>
            <div class="pipe-name">{{ name }}</div>
            <el-icon v-if="idx < stepList.length - 1" class="pipe-arrow"><ArrowRight /></el-icon>
          </div>
        </div>
        <div class="models">
          <div class="model-row"><span>文本</span><b>DeepSeek</b></div>
          <div class="model-row"><span>图像</span><b>Seedream</b></div>
          <div class="model-row"><span>语音</span><b>Seed-TTS</b></div>
          <div class="model-row"><span>视频</span><b>Seedance</b></div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import {
  Plus,
  Box,
  ArrowRight,
  Film,
  Loading,
  CircleCheck,
  Warning
} from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { pageTask, type TaskItem } from '@/api/task'
import { TaskStatus, statusMeta, STEP_NAMES } from '@/constants/task'
import { Pencil, Doodles, StickyNote } from '@/components/illustrations'

const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const recentTasks = ref<TaskItem[]>([])

const stepList = Object.values(STEP_NAMES)

const statCounts = ref({ total: 0, running: 0, done: 0, failed: 0 })

const stats = computed(() => [
  { key: 'total', label: '任务总数', value: statCounts.value.total, icon: Film, color: 'var(--cd-primary)' },
  { key: 'running', label: '生成中', value: statCounts.value.running, icon: Loading, color: 'var(--cd-warning)' },
  { key: 'done', label: '已完成', value: statCounts.value.done, icon: CircleCheck, color: 'var(--cd-success)' },
  { key: 'failed', label: '失败', value: statCounts.value.failed, icon: Warning, color: 'var(--cd-danger)' }
])

function progressColor(status?: number) {
  const m = statusMeta(status)
  return `var(${m.colorVar})`
}

function fmtTime(t?: string) {
  if (!t) return ''
  return t.replace('T', ' ').slice(0, 16)
}

async function loadDashboard() {
  loading.value = true
  try {
    const res = await pageTask({ page: 1, size: 5 })
    recentTasks.value = res.records || []
  } catch (e) {
    /* 拦截器提示 */
  } finally {
    loading.value = false
  }
}

async function loadStats() {
  // Phase-1：拉一次较大分页聚合统计（简化实现，Phase-2 改专用统计接口）
  try {
    const res = await pageTask({ page: 1, size: 200 })
    const list = res.records || []
    statCounts.value = {
      total: res.total || list.length,
      running: list.filter((t) => t.status === TaskStatus.RUNNING || t.status === TaskStatus.QUEUE).length,
      done: list.filter((t) => t.status === TaskStatus.DONE).length,
      failed: list.filter((t) => t.status === TaskStatus.FAILED).length
    }
  } catch (e) {
    /* 忽略 */
  }
}

onMounted(() => {
  loadDashboard()
  loadStats()
})
</script>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.welcome {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24px 28px;
  gap: 16px;
  flex-wrap: wrap;
}
.welcome-decor {
  display: flex;
  align-items: center;
  gap: 8px;
  opacity: 0.85;
}
.welcome-text h2 {
  margin: 0 0 6px;
  font-size: 22px;
  font-weight: 800;
  color: var(--cd-text);
}
.welcome-text p {
  margin: 0;
  color: var(--cd-text-secondary);
  font-size: 14px;
}
.create-btn {
  height: 42px;
  white-space: nowrap;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}
.stat-card {
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 14px;
}
.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 22px;
  flex-shrink: 0;
  box-shadow: 2px 2px 0 0 var(--cd-shadow);
}
.stat-value {
  font-size: 26px;
  font-weight: 800;
  color: var(--cd-text);
  line-height: 1;
}
.stat-label {
  margin-top: 4px;
  font-size: 13px;
  color: var(--cd-text-secondary);
}

.dual-grid {
  display: grid;
  grid-template-columns: 1.4fr 1fr;
  gap: 20px;
}

.panel {
  padding: 20px 22px;
}
.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.panel-head h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: var(--cd-text);
}
.more {
  font-size: 13px;
  color: var(--cd-primary);
  cursor: pointer;
}
.more:hover {
  color: var(--cd-primary-hover);
}

.empty {
  text-align: center;
  padding: 24px 0;
  color: var(--cd-text-secondary);
}
.empty p {
  margin: 10px 0 0;
  font-size: 13px;
}

.task-row {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 10px 8px;
  border-radius: 8px;
  cursor: pointer;
  transition: background-color 0.15s ease;
}
.task-row:hover {
  background-color: var(--cd-bg-soft);
}
.task-row-main {
  flex: 1;
  min-width: 0;
}
.task-title {
  font-weight: 600;
  color: var(--cd-text);
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.task-sub {
  font-size: 12px;
  color: var(--cd-text-secondary);
  margin-top: 2px;
}
.task-progress {
  width: 120px;
  flex-shrink: 0;
}

.pipeline {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.pipe-step {
  display: flex;
  align-items: center;
  gap: 10px;
}
.pipe-num {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background-color: var(--cd-primary);
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.pipe-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--cd-text);
}
.pipe-arrow {
  margin-left: auto;
  color: var(--cd-text-secondary);
  opacity: 0.5;
  transform: rotate(90deg);
}

.models {
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1.5px dashed var(--cd-border);
}
.model-row {
  display: flex;
  justify-content: space-between;
  padding: 6px 0;
  font-size: 13px;
}
.model-row span {
  color: var(--cd-text-secondary);
}
.model-row b {
  color: var(--cd-text);
  font-weight: 600;
}

@media (max-width: 1024px) {
  .stat-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .dual-grid {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 480px) {
  .stat-grid {
    grid-template-columns: 1fr;
  }
  .task-progress {
    display: none;
  }
}
</style>
