<template>
  <div class="work-detail" v-loading="loading">
    <div v-if="work" class="detail-content">
      <div class="detail-header">
        <button class="back-btn" @click="router.back()">
          <el-icon><ArrowLeft /></el-icon>
          返回
        </button>
        <h2>{{ work.title || '未命名作品' }}</h2>
        <el-tag :type="work.status === 1 ? 'success' : 'info'" effect="light" round>
          {{ work.status === 1 ? '已发布' : '草稿' }}
        </el-tag>
      </div>

      <el-alert
        v-if="work.taskId"
        type="info"
        :closable="false"
        show-icon
        class="task-ref-alert"
      >
        <template #title>
          <span>此作品由任务生成，按任务反查：</span>
          <el-tag type="primary" effect="dark" round style="margin-left: 6px; cursor: pointer" @click="goToTask">
            #{{ work.taskId }}
          </el-tag>
        </template>
      </el-alert>

      <div class="player-section sketch-card">
        <div class="player-wrapper" v-if="work.videoUrl">
          <video
            ref="videoRef"
            :src="work.videoUrl"
            :poster="work.coverUrl"
            controls
            class="video-player"
          >
            您的浏览器不支持视频播放
          </video>
        </div>
        <div v-else class="player-placeholder">
          <el-icon :size="64"><VideoPlay /></el-icon>
          <p>视频资源未就绪</p>
        </div>

        <div class="work-meta-bar">
          <div class="meta-item">
            <span class="meta-label">时长</span>
            <span class="meta-value">{{ formatDuration(work.duration) }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-label">分辨率</span>
            <span class="meta-value">{{ work.resolution }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-label">画幅</span>
            <span class="meta-value">{{ work.aspectRatio }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-label">大小</span>
            <span class="meta-value">{{ formatFileSize(work.fileSize) }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-label">播放</span>
            <span class="meta-value">{{ work.viewCount || 0 }} 次</span>
          </div>
        </div>
      </div>

      <div class="timeline-section sketch-card">
        <div class="section-header">
          <h3>分镜时间线</h3>
          <div class="section-actions">
            <span class="section-hint">共 {{ timeline.length }} 个分镜</span>
            <button class="sketch-btn sketch-btn--ghost btn-sm" :disabled="timeline.length < 2" @click="handleReorderTimeline">
              <el-icon><Sort /></el-icon>
              一键重排
            </button>
            <button class="sketch-btn btn-sm" @click="openCreateTimeline">
              <el-icon><Plus /></el-icon>
              新增时间线条目
            </button>
          </div>
        </div>
        <div class="timeline-track">
          <div
            v-for="(item, index) in timeline"
            :key="item.id"
            class="timeline-item"
            :class="{ active: activeTimeline === index }"
          >
            <div class="timeline-thumb" @click="seekToTimeline(index)">
              <div class="thumb-index">{{ index + 1 }}</div>
              <span class="thumb-duration">{{ formatDuration(item.duration) }}</span>
            </div>
            <div class="timeline-item-actions">
              <el-button link type="primary" size="small" @click="openEditTimeline(item)">编辑</el-button>
              <el-button link type="danger" size="small" @click="handleDeleteTimeline(item)">删除</el-button>
            </div>
          </div>
          <div v-if="timeline.length === 0" class="empty-timeline">
            <p>暂无时间线条目，点击「新增时间线条目」添加</p>
          </div>
        </div>
      </div>

      <div class="scenes-section sketch-card">
        <div class="section-header">
          <h3>场景列表</h3>
        </div>
        <div class="scene-list">
          <div
            v-for="(item, index) in timeline"
            :key="'scene-' + item.id"
            class="scene-item"
            :class="{ active: activeTimeline === index }"
          >
            <div class="scene-index" @click="seekToTimeline(index)">{{ index + 1 }}</div>
            <div class="scene-info" @click="seekToTimeline(index)">
              <div class="scene-title">场景 {{ index + 1 }}</div>
              <div class="scene-meta">
                <span>时长: {{ formatDuration(item.duration) }}</span>
                <span v-if="item.sceneGroupId" class="meta-sep">|</span>
                <span v-if="item.sceneGroupId">场景组ID: {{ item.sceneGroupId }}</span>
                <span v-if="item.storyboardId" class="meta-sep">|</span>
                <span v-if="item.storyboardId">分镜ID: {{ item.storyboardId }}</span>
              </div>
              <div v-if="item.videoUrl" class="scene-video-url" :title="item.videoUrl">
                视频: {{ truncateUrl(item.videoUrl) }}
              </div>
            </div>
            <div class="scene-actions">
              <el-button link type="primary" size="small" @click="openEditTimeline(item)">编辑</el-button>
              <el-button link type="danger" size="small" @click="handleDeleteTimeline(item)">删除</el-button>
              <el-icon class="scene-play" @click="seekToTimeline(index)"><VideoPlay /></el-icon>
            </div>
          </div>
          <div v-if="timeline.length === 0" class="empty-scenes">
            <p>暂无分镜数据</p>
          </div>
        </div>
      </div>

      <div v-if="work.description" class="description-section sketch-card">
        <div class="section-header">
          <h3>作品描述</h3>
        </div>
        <p class="description-text">{{ work.description }}</p>
      </div>
    </div>

    <el-dialog
      v-model="timelineDialogVisible"
      :title="editingTimeline ? '编辑时间线条目' : '新增时间线条目'"
      width="560px"
      :close-on-click-modal="false"
    >
      <el-form ref="timelineFormRef" :model="timelineForm" :rules="timelineFormRules" label-width="120px">
        <el-form-item label="场景组ID">
          <el-input-number v-model="timelineForm.sceneGroupId" :min="0" :step="1" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="分镜ID">
          <el-input-number v-model="timelineForm.storyboardId" :min="0" :step="1" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="视频URL" prop="videoUrl">
          <el-input v-model="timelineForm.videoUrl" placeholder="视频文件访问地址" />
        </el-form-item>
        <el-form-item label="排序序号" prop="orderIndex">
          <el-input-number v-model="timelineForm.orderIndex" :min="0" :step="1" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="时长(秒)">
          <el-input-number v-model="timelineForm.duration" :min="0" :step="1" :precision="0" controls-position="right" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <button class="sketch-btn sketch-btn--ghost" @click="timelineDialogVisible = false">取消</button>
        <button class="sketch-btn" :disabled="timelineSubmitting" @click="handleSubmitTimeline">
          {{ timelineSubmitting ? '保存中...' : '确认' }}
        </button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, computed, nextTick, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { ArrowLeft, VideoPlay, Plus, Sort } from '@element-plus/icons-vue'
import {
  getWork,
  getWorkTimeline,
  createWorkTimeline,
  updateWorkTimeline,
  deleteWorkTimeline,
  reorderWorkTimeline,
  type WorkItem,
  type WorkTimelineItem
} from '@/api/work'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const workId = computed(() => Number(route.params.id))
const work = ref<WorkItem | null>(null)
const timeline = ref<WorkTimelineItem[]>([])
const activeTimeline = ref(-1)
const videoRef = ref<HTMLVideoElement>()

function formatDuration(seconds?: number) {
  if (!seconds) return '0:00'
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  if (m < 60) return `${m}:${String(s).padStart(2, '0')}`
  const h = Math.floor(m / 60)
  return `${h}:${String(m % 60).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

function formatFileSize(bytes?: number) {
  if (!bytes) return '0 B'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`
  return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`
}

function truncateUrl(url: string) {
  if (!url) return '-'
  if (url.length <= 50) return url
  return url.slice(0, 24) + '...' + url.slice(-24)
}

function goToTask() {
  if (work.value?.taskId) {
    router.push(`/task/${work.value.taskId}`)
  }
}

async function loadWork() {
  loading.value = true
  try {
    const [w, t] = await Promise.all([
      getWork(workId.value),
      getWorkTimeline(workId.value)
    ])
    work.value = w
    timeline.value = Array.isArray(t) ? [...t].sort((a, b) => (a.orderIndex ?? 0) - (b.orderIndex ?? 0)) : []
  } catch (e) {
    console.error('Failed to load work', e)
  } finally {
    loading.value = false
  }
}

function seekToTimeline(index: number) {
  if (videoRef.value && timeline.value[index]) {
    activeTimeline.value = index
    videoRef.value.play()
  }
}

// ===== 时间线条目 =====
const timelineDialogVisible = ref(false)
const timelineSubmitting = ref(false)
const editingTimeline = ref<WorkTimelineItem | null>(null)
const timelineFormRef = ref<FormInstance>()
const timelineForm = reactive({
  id: 0,
  sceneGroupId: 0,
  storyboardId: 0,
  videoUrl: '',
  orderIndex: 0,
  duration: 0
})
const timelineFormRules: FormRules = {
  videoUrl: [{ required: true, message: '请输入视频URL', trigger: 'blur' }],
  orderIndex: [{ required: true, message: '请输入排序序号', trigger: 'blur' }]
}

function openCreateTimeline() {
  editingTimeline.value = null
  const maxOrder = timeline.value.length > 0
    ? Math.max(...timeline.value.map(t => t.orderIndex ?? 0))
    : -1
  Object.assign(timelineForm, {
    id: 0,
    sceneGroupId: 0,
    storyboardId: 0,
    videoUrl: '',
    orderIndex: maxOrder + 1,
    duration: 0
  })
  timelineDialogVisible.value = true
}

function openEditTimeline(item: WorkTimelineItem) {
  editingTimeline.value = item
  Object.assign(timelineForm, {
    id: item.id,
    sceneGroupId: item.sceneGroupId ?? 0,
    storyboardId: item.storyboardId ?? 0,
    videoUrl: item.videoUrl || '',
    orderIndex: item.orderIndex ?? 0,
    duration: item.duration ?? 0
  })
  timelineDialogVisible.value = true
}

async function handleSubmitTimeline() {
  if (!timelineFormRef.value) return
  await timelineFormRef.value.validate(async (valid) => {
    if (!valid) return
    timelineSubmitting.value = true
    try {
      const payload: any = {
        workId: workId.value,
        videoUrl: timelineForm.videoUrl,
        orderIndex: timelineForm.orderIndex
      }
      if (timelineForm.sceneGroupId > 0) payload.sceneGroupId = timelineForm.sceneGroupId
      if (timelineForm.storyboardId > 0) payload.storyboardId = timelineForm.storyboardId
      if (timelineForm.duration > 0) payload.duration = timelineForm.duration

      if (editingTimeline.value) {
        await updateWorkTimeline(editingTimeline.value.id, payload)
        ElMessage.success('条目已更新')
      } else {
        await createWorkTimeline(payload)
        ElMessage.success('条目已新增')
      }
      timelineDialogVisible.value = false
      await loadWork()
    } catch (e: any) {
      ElMessage.error(e?.message || '保存失败')
    } finally {
      timelineSubmitting.value = false
    }
  })
}

async function handleDeleteTimeline(item: WorkTimelineItem) {
  try {
    await ElMessageBox.confirm(
      `确定删除时间线条目（序号 ${item.orderIndex}）吗？`,
      '删除条目',
      { type: 'error', confirmButtonText: '删除', confirmButtonClass: 'el-button--danger' }
    )
    await deleteWorkTimeline(item.id)
    ElMessage.success('已删除')
    await loadWork()
  } catch (_) { /* 用户取消 */ }
}

async function handleReorderTimeline() {
  if (timeline.value.length < 2) return
  try {
    await ElMessageBox.confirm(
      `将按当前顺序（1~${timeline.value.length}）重置所有条目的 orderIndex，是否继续？`,
      '一键重排',
      { type: 'warning', confirmButtonText: '确认重排' }
    )
    const payload = timeline.value.map((t, idx) => ({
      id: t.id,
      orderIndex: idx
    }))
    await reorderWorkTimeline(payload)
    ElMessage.success('重排完成')
    await loadWork()
  } catch (_) { /* 用户取消或请求失败 */ }
}

onMounted(loadWork)
</script>

<style scoped>
.work-detail {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.detail-content {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.detail-header {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
}
.back-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border: 1.5px solid var(--cd-border);
  border-radius: 6px;
  background: transparent;
  color: var(--cd-text);
  font-weight: 600;
  cursor: pointer;
  transition: background-color 0.15s ease;
}
.back-btn:hover {
  background-color: var(--cd-bg-soft);
}
.detail-header h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 800;
  color: var(--cd-text);
}

.task-ref-alert {
  margin: 0;
}

.player-section {
  padding: 0;
  overflow: hidden;
}
.player-wrapper {
  background-color: #000;
  width: 100%;
  max-height: 500px;
}
.video-player {
  width: 100%;
  max-height: 500px;
  display: block;
}
.player-placeholder {
  height: 300px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--cd-text-secondary);
  gap: 12px;
}

.work-meta-bar {
  display: flex;
  padding: 16px 20px;
  border-top: 1.5px solid var(--cd-border);
  background-color: var(--cd-bg-card);
  gap: 28px;
  flex-wrap: wrap;
}
.meta-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.meta-label {
  font-size: 12px;
  color: var(--cd-text-secondary);
}
.meta-value {
  font-size: 15px;
  font-weight: 700;
  color: var(--cd-text);
}

.timeline-section,
.scenes-section,
.description-section {
  padding: 20px;
}
.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  gap: 12px;
  flex-wrap: wrap;
}
.section-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: var(--cd-text);
}
.section-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}
.section-hint {
  font-size: 13px;
  color: var(--cd-text-secondary);
}

.btn-sm {
  padding: 5px 10px;
  font-size: 12px;
}

.timeline-track {
  display: flex;
  gap: 10px;
  overflow-x: auto;
  padding-bottom: 8px;
  flex-wrap: wrap;
}
.timeline-item {
  flex-shrink: 0;
  width: 120px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  transition: transform 0.15s ease;
}
.timeline-item:hover {
  transform: translateY(-2px);
}
.timeline-item.active .timeline-thumb {
  border-color: var(--cd-primary);
  box-shadow: 0 0 0 2px var(--cd-primary);
}
.timeline-thumb {
  width: 120px;
  height: 68px;
  border: 1.5px solid var(--cd-border);
  border-radius: 6px;
  background-color: var(--cd-bg-soft);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  transition: border-color 0.15s ease;
  cursor: pointer;
}
.thumb-index {
  font-size: 20px;
  font-weight: 800;
  color: var(--cd-text);
}
.thumb-duration {
  font-size: 11px;
  color: var(--cd-text-secondary);
}
.timeline-item-actions {
  display: flex;
  justify-content: center;
  gap: 4px;
}

.empty-timeline {
  flex: 1;
  width: 100%;
  text-align: center;
  padding: 30px;
  color: var(--cd-text-secondary);
  border: 1.5px dashed var(--cd-border);
  border-radius: 8px;
}

.scene-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.scene-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 14px;
  border: 1.5px solid var(--cd-border);
  border-radius: 8px;
  transition: all 0.15s ease;
}
.scene-item:hover {
  background-color: var(--cd-bg-soft);
}
.scene-item.active {
  border-color: var(--cd-primary);
  background-color: var(--cd-bg-soft);
}
.scene-index {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background-color: var(--cd-primary);
  color: #fff;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  cursor: pointer;
}
.scene-info {
  flex: 1;
  cursor: pointer;
}
.scene-title {
  font-weight: 600;
  color: var(--cd-text);
}
.scene-meta {
  font-size: 12px;
  color: var(--cd-text-secondary);
  margin-top: 2px;
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.meta-sep {
  color: var(--cd-border);
}
.scene-video-url {
  margin-top: 4px;
  font-size: 12px;
  color: var(--cd-text-secondary);
  opacity: 0.7;
}
.scene-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.scene-play {
  color: var(--cd-text-secondary);
  cursor: pointer;
}
.empty-scenes {
  text-align: center;
  padding: 24px;
  color: var(--cd-text-secondary);
}

.description-text {
  margin: 0;
  font-size: 14px;
  line-height: 1.8;
  color: var(--cd-text);
  white-space: pre-wrap;
}

@media (max-width: 768px) {
  .work-meta-bar {
    gap: 20px;
  }
  .timeline-track {
    gap: 8px;
  }
  .timeline-item {
    width: 96px;
  }
  .timeline-thumb {
    width: 96px;
    height: 56px;
  }
}
</style>
