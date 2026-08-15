<template>
  <div class="work-list">
    <div class="page-header">
      <h2>我的作品</h2>
    </div>

    <div v-loading="loading" class="work-grid">
      <div v-if="workList.length === 0 && !loading" class="empty-state">
        <div class="empty-icon">🎬</div>
        <p>还没有作品，完成任务后作品会出现在这里</p>
        <button class="sketch-btn" @click="router.push('/task/create')">
          <el-icon><Plus /></el-icon>
          创建任务生成作品
        </button>
      </div>

      <div
        v-for="work in workList"
        :key="work.id"
        class="work-card sketch-card"
      >
        <div class="cover-wrapper" @click="openPlayer(work)">
          <img
            v-if="work.coverUrl"
            :src="work.coverUrl"
            :alt="decodeTitle(work.title)"
            class="cover"
          />
          <div v-else class="cover-placeholder">
            <el-icon :size="48"><Film /></el-icon>
          </div>
          <div class="cover-overlay">
            <el-icon :size="24"><VideoPlay /></el-icon>
          </div>
          <el-tag type="success" effect="dark" class="status-tag" round>
            已完成
          </el-tag>
          <div class="work-card-actions" @click.stop>
            <el-button link type="primary" size="small" @click="openEditWork(work)">编辑</el-button>
            <el-button link type="primary" size="small" @click="openPlayer(work)">观看</el-button>
            <el-button link type="primary" size="small" @click="openShare(work)">分享</el-button>
          </div>
        </div>
        <div class="work-info" @click="openPlayer(work)">
          <div class="work-title">{{ decodeTitle(work.title) || '未命名作品' }}</div>
          <div class="work-meta">
            <span class="meta-item">
              <el-icon><Clock /></el-icon>
              {{ formatDuration(work.duration) }}
            </span>
            <span class="meta-item">
              <el-icon><Monitor /></el-icon>
              {{ work.resolution }}
            </span>
            <span class="meta-item">
              <el-icon><View /></el-icon>
              {{ formatNumber(work.viewCount) }}
            </span>
          </div>
          <div class="work-footer">
            <span class="work-time">{{ formatTime(work.createTime) }}</span>
            <el-button
              v-if="work.taskId"
              link
              type="primary"
              size="small"
              class="task-link"
              @click.stop="goToTask(work)"
            >
              <el-icon><Link /></el-icon>
              查看任务
            </el-button>
          </div>
        </div>
      </div>
    </div>

    <div class="pagination" v-if="total > 0">
      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        :page-sizes="[12, 24, 48]"
        layout="total, sizes, prev, pager, next"
        background
        @current-change="loadData"
        @size-change="handleSizeChange"
      />
    </div>

    <!-- 视频播放弹窗 -->
    <el-dialog
      v-model="playerVisible"
      :title="currentWork ? decodeTitle(currentWork.title) : '视频播放'"
      width="1060px"
      style="max-width: 95vw"
      :close-on-click-modal="true"
      align-center
      destroy-on-close
    >
      <div class="player-dialog-content" v-if="currentWork">
        <div class="video-and-list-wrapper">
          <div class="video-main-area">
            <video
              v-if="currentVideo"
              :key="currentVideo"
              ref="playerVideoRef"
              :src="currentVideo"
              :poster="currentWork.coverUrl"
              controls
              autoplay
              class="dialog-video"
              @ended="handleVideoEnded"
            >
              您的浏览器不支持视频播放
            </video>
            <div v-else class="player-dialog-empty">
              <el-icon :size="64"><VideoPlay /></el-icon>
              <p>
                {{ segments.length > 0 ? '正在加载视频...' :
                   isZipWork(currentWork) ? '作品为 ZIP 包格式，正在尝试从场景视频加载直链' : '该作品暂无视频资源' }}
              </p>
              <p v-if="segments.length === 0 && currentWork.zipUrl" class="empty-hint">
                可直接下载成片 ZIP 包
              </p>
              <div class="empty-actions">
                <button v-if="currentWork.zipUrl" class="sketch-btn" @click="downloadZip(currentWork)">
                  <el-icon><Download /></el-icon>
                  下载成片 ZIP
                </button>
                <button v-if="currentWork.taskId" class="sketch-btn sketch-btn--ghost" @click="goToTask(currentWork)">
                  <el-icon><Link /></el-icon>
                  前往任务查看
                </button>
              </div>
            </div>

            <div v-if="segments.length > 1" class="segment-control-bar">
              <el-icon
                class="seg-btn"
                :class="{ disabled: currentSegIndex <= 0 }"
                @click="playPrev"
              ><CaretLeft /></el-icon>
              <span class="seg-info">
                第 {{ currentSegIndex + 1 }} / {{ segments.length }} 段
                <span v-if="currentSegMeta"> · 时长 {{ formatSegDuration(currentSegMeta.duration) }}</span>
              </span>
              <el-icon
                class="seg-btn"
                :class="{ disabled: currentSegIndex >= segments.length - 1 }"
                @click="playNext"
              ><CaretRight /></el-icon>
            </div>
          </div>

          <div v-if="segments.length > 0" class="segment-list-area">
            <div class="seg-list-title">分镜视频列表</div>
            <div class="seg-list-scroll">
              <div
                v-for="(seg, idx) in segments"
                :key="idx"
                class="seg-item"
                :class="{ active: idx === currentSegIndex }"
                @click="switchSegment(idx)"
              >
                <div class="seg-thumb">
                  <img v-if="seg.thumbnailUrl" :src="seg.thumbnailUrl" />
                  <el-icon v-else><Film /></el-icon>
                  <span class="seg-index-label">{{ idx + 1 }}</span>
                </div>
                <div class="seg-meta">
                  <div class="seg-name">分镜 #{{ idx + 1 }}</div>
                  <div class="seg-sub">{{ formatSegDuration(seg.duration) }}</div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </el-dialog>

    <!-- 分享弹窗 -->
    <el-dialog
      v-model="shareVisible"
      title="分享作品"
      width="460px"
      :close-on-click-modal="true"
    >
      <div v-if="shareWork" class="share-content">
        <div class="share-work-info">
          <img v-if="shareWork.coverUrl" :src="shareWork.coverUrl" class="share-cover" />
          <div class="share-work-meta">
            <div class="share-title">{{ decodeTitle(shareWork.title) }}</div>
            <div class="share-desc">分享给其他用户查看完成的视频</div>
          </div>
        </div>
        <div v-if="shareUrl" class="share-link-section">
          <div class="share-label">分享链接</div>
          <div class="share-link-box">
            <el-input v-model="shareUrl" readonly @focus="($event.target as HTMLInputElement).select()" />
          </div>
          <div class="share-actions">
            <button class="sketch-btn sketch-btn--primary" @click="copyShareUrl">
              <el-icon><CopyDocument /></el-icon>
              复制链接
            </button>
          </div>
          <div class="share-expire">链接有效期：72 小时</div>
        </div>
        <div v-else class="share-generating">
          <el-icon class="spin-icon"><Loading /></el-icon>
          <span>正在生成分享链接...</span>
        </div>
      </div>
    </el-dialog>

    <!-- 编辑作品对话框 -->
    <el-dialog
      v-model="editDialogVisible"
      title="编辑作品"
      width="560px"
      :close-on-click-modal="false"
    >
      <el-form ref="editFormRef" :model="editForm" :rules="editFormRules" label-width="100px">
        <el-form-item label="作品标题" prop="title">
          <el-input v-model="editForm.title" placeholder="请输入标题" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="作品描述">
          <el-input v-model="editForm.description" type="textarea" :rows="3" placeholder="选填" />
        </el-form-item>

        <!-- 封面选择区域 -->
        <el-form-item label="作品封面">
          <div class="cover-selector">
            <!-- 封面预览 -->
            <div v-if="editForm.coverUrl" class="cover-preview">
              <img :src="editForm.coverUrl" class="preview-img" />
              <button class="remove-cover" @click="editForm.coverUrl = ''">
                <el-icon><Close /></el-icon>
              </button>
            </div>
            <div v-else class="cover-preview-placeholder">
              <el-icon :size="36"><Picture /></el-icon>
              <span>选择封面</span>
            </div>

            <!-- 三种选择方式 -->
            <el-tabs v-model="coverTab" class="cover-tabs" v-if="editingWork">
              <el-tab-pane label="上传图片" name="upload">
                <el-upload
                  :auto-upload="true"
                  :show-file-list="false"
                  :before-upload="beforeCoverUpload"
                  :http-request="handleCoverUpload"
                  accept="image/*"
                  class="cover-uploader"
                >
                  <div class="uploader-btn">
                    <el-icon :size="24"><UploadFilled /></el-icon>
                    <span>点击上传图片</span>
                  </div>
                </el-upload>
              </el-tab-pane>

              <el-tab-pane label="从资产选择" name="asset">
                <div v-if="assetImages.length === 0" class="no-assets">
                  <el-icon :size="32"><Picture /></el-icon>
                  <span>暂无资产图片</span>
                </div>
                <div v-else class="asset-grid">
                  <div
                    v-for="img in assetImages"
                    :key="img.id"
                    class="asset-item"
                    :class="{ active: editForm.coverUrl === img.imageUrl }"
                    @click="editForm.coverUrl = img.imageUrl"
                  >
                    <img :src="img.thumbnailUrl || img.imageUrl" :alt="img.assetName || '资产'" />
                    <span class="asset-name">{{ img.assetName || '资产' }}</span>
                  </div>
                </div>
              </el-tab-pane>

              <el-tab-pane label="输入URL" name="url">
                <el-input v-model="editForm.coverUrl" placeholder="图片URL" />
              </el-tab-pane>
            </el-tabs>
          </div>
        </el-form-item>

        <el-form-item label="是否公开">
          <el-radio-group v-model="editForm.isPublic">
            <el-radio :value="1">公开</el-radio>
            <el-radio :value="0">不公开</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <button class="sketch-btn sketch-btn--ghost" @click="editDialogVisible = false">取消</button>
        <button class="sketch-btn" :disabled="editSubmitting" @click="handleSubmitEdit">
          {{ editSubmitting ? '保存中...' : '保存' }}
        </button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import {
  Plus, Film, VideoPlay, Clock, Monitor, View, Link,
  Download, CopyDocument, Close, Picture, UploadFilled, Loading,
  CaretLeft, CaretRight
} from '@element-plus/icons-vue'
import {
  pageWork,
  updateWork,
  getAssetImagesByTask,
  uploadFile,
  generateWorkShareToken,
  getWorkTimeline,
  listSceneVideosByTask,
  type WorkItem,
  type AssetImageItem,
  type WorkTimelineItem,
  type SceneVideoItem
} from '@/api/work'

const router = useRouter()
const loading = ref(false)
const workList = ref<WorkItem[]>([])
const total = ref(0)

const query = reactive({
  page: 1,
  size: 12
})

function decodeTitle(title?: string): string {
  if (!title) return ''
  try {
    const decoded = decodeURIComponent(title)
    if (decoded !== title && !/[\u4e00-\u9fa5]/.test(title)) {
      return decoded
    }
    return title
  } catch {
    return title
  }
}

function formatDuration(seconds?: number) {
  if (!seconds) return '0s'
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  if (m < 60) return `${m}m ${s}s`
  const h = Math.floor(m / 60)
  return `${h}h ${m % 60}m`
}

function formatNumber(n?: number) {
  if (!n) return '0'
  if (n >= 10000) return `${(n / 10000).toFixed(1)}w`
  if (n >= 1000) return `${(n / 1000).toFixed(1)}k`
  return String(n)
}

function formatTime(t?: string) {
  if (!t) return ''
  return t.replace('T', ' ').slice(0, 16)
}

async function loadData() {
  loading.value = true
  try {
    const res = await pageWork(query)
    workList.value = (res.records || []).map(w => ({
      ...w,
      title: decodeTitle(w.title)
    }))
    total.value = res.total || 0
  } catch (e) {
    console.error('Failed to load works', e)
  } finally {
    loading.value = false
  }
}

function handleSizeChange() {
  query.page = 1
  loadData()
}

function getPlayableUrl(work?: WorkItem) {
  if (!work) return ''
  const video = work.videoUrl
  if (video && !/\.(zip|ZIP)$/.test(video.split('?')[0])) {
    return video
  }
  return ''
}

function isZipWork(work?: WorkItem) {
  if (!work) return false
  const v = work.videoUrl || ''
  if (/\.(zip|ZIP)$/.test(v.split('?')[0])) return true
  if (work.zipUrl) return true
  return false
}

// ===== 视频播放 =====
type SegVideo = {
  videoUrl: string
  thumbnailUrl?: string
  duration?: number
}
const playerVisible = ref(false)
const playerVideoRef = ref<HTMLVideoElement>()
const currentWork = ref<WorkItem | null>(null)
const segments = ref<SegVideo[]>([])
const currentSegIndex = ref(0)
const loadingSegments = ref(false)

const currentVideo = computed(() => segments.value[currentSegIndex.value]?.videoUrl || getPlayableUrl(currentWork.value || undefined))
const currentSegMeta = computed(() => segments.value[currentSegIndex.value])

async function openPlayer(work: WorkItem) {
  currentWork.value = work
  segments.value = []
  currentSegIndex.value = 0
  playerVisible.value = true
  loadingSegments.value = true
  try {
    await loadSegments(work)
  } finally {
    loadingSegments.value = false
  }
}

async function loadSegments(work: WorkItem) {
  // 1) 优先 comic_work_timeline（时间线，保证顺序 - 按 orderIndex ASC 重排以防接口漏排）
  if (work.id) {
    try {
      const tl = await getWorkTimeline(work.id)
      if (Array.isArray(tl) && tl.length > 0) {
        const sortedTl = [...tl].sort(
          (a, b) => (a.orderIndex ?? -1) - (b.orderIndex ?? -1)
        )
        segments.value = sortedTl.map((t: WorkTimelineItem) => ({
          videoUrl: t.videoUrl,
          thumbnailUrl: undefined,
          duration: t.duration
        }))
        currentSegIndex.value = 0
        return
      }
    } catch (_) { /* timeline 表可能不存在 */ }
  }

  // 2) 兜底 scene_video 表（按 taskId 查，所有分镜视频 URL 都存这里）
  if (work.taskId) {
    try {
      const svList = await listSceneVideosByTask(work.taskId)
      const valid = (Array.isArray(svList) ? svList : []).filter(
        (s: SceneVideoItem) => s && s.videoUrl && (s.status == null || s.status === 2)
      )
      if (valid.length > 0) {
        valid.sort((a: SceneVideoItem, b: SceneVideoItem) => {
          const ag = a.sceneGroupId ?? 0
          const bg = b.sceneGroupId ?? 0
          if (ag !== bg) return ag - bg
          // storyboardIds 格式为 "minSeq,maxSeq"，按 minSeq 排序保持分镜顺序
          const aSeq = parseStartSeq(a.storyboardIds)
          const bSeq = parseStartSeq(b.storyboardIds)
          if (aSeq !== bSeq) return aSeq - bSeq
          return (a.id || 0) - (b.id || 0)
        })
        segments.value = valid.map((s: SceneVideoItem) => ({
          videoUrl: s.videoUrl,
          thumbnailUrl: s.thumbnailUrl,
          duration: s.duration
        }))
        currentSegIndex.value = 0
        return
      }
    } catch (_) { /* 忽略 */ }
  }

  segments.value = []
}

function parseStartSeq(ids?: string) {
  if (!ids) return Number.MAX_SAFE_INTEGER
  try {
    const i = ids.indexOf(',')
    const first = (i > 0 ? ids.slice(0, i) : ids).trim()
    const n = Number(first)
    return Number.isFinite(n) ? n : Number.MAX_SAFE_INTEGER
  } catch {
    return Number.MAX_SAFE_INTEGER
  }
}

function playPrev() {
  if (currentSegIndex.value > 0) switchSegment(currentSegIndex.value - 1)
}
function playNext() {
  if (currentSegIndex.value < segments.value.length - 1) switchSegment(currentSegIndex.value + 1)
}
function switchSegment(idx: number) {
  currentSegIndex.value = idx
  setTimeout(() => {
    if (playerVideoRef.value) {
      playerVideoRef.value.load()
      playerVideoRef.value.play().catch(() => { /* 浏览器拦截自动播放则静默 */ })
    }
  }, 0)
}
function handleVideoEnded() {
  // 自动播放下一段
  if (currentSegIndex.value < segments.value.length - 1) {
    playNext()
  }
}

function formatSegDuration(d?: number | string) {
  if (!d) return '0:00'
  let sec: number
  if (typeof d === 'number') sec = Math.round(d)
  else sec = Number(d) || 0
  const m = Math.floor(sec / 60)
  const s = sec % 60
  return `${m}:${String(s).padStart(2, '0')}`
}

// ===== 下载ZIP =====
function downloadZip(work: WorkItem) {
  const url = work.zipUrl
  if (!url) {
    ElMessage.warning('暂无成片包')
    return
  }
  const a = document.createElement('a')
  a.href = url
  a.download = `${decodeTitle(work.title) || '作品'}.zip`
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
}

// ===== 跳转任务 =====
function goToTask(work: WorkItem) {
  if (work.taskId) {
    router.push(`/task/${work.taskId}`)
  }
}

// ===== 分享 =====
const shareVisible = ref(false)
const shareWork = ref<WorkItem | null>(null)
const shareUrl = ref('')

async function openShare(work: WorkItem) {
  shareWork.value = work
  shareUrl.value = ''
  shareVisible.value = true
  try {
    const token = await generateWorkShareToken(work.id, 72)
    const origin = window.location.origin
    shareUrl.value = `${origin}/work/share/${token}`
  } catch (e: any) {
    ElMessage.error(e?.message || '生成分享链接失败')
    shareVisible.value = false
  }
}

function copyShareUrl() {
  if (!shareUrl.value) return
  navigator.clipboard.writeText(shareUrl.value).then(() => {
    ElMessage.success('分享链接已复制到剪贴板')
  }).catch(() => {
    const ta = document.createElement('textarea')
    ta.value = shareUrl.value
    document.body.appendChild(ta)
    ta.select()
    document.execCommand('copy')
    document.body.removeChild(ta)
    ElMessage.success('分享链接已复制')
  })
}

// ===== 编辑作品 =====
const editDialogVisible = ref(false)
const editSubmitting = ref(false)
const editFormRef = ref<FormInstance>()
const editingWork = ref<WorkItem | null>(null)
const editForm = reactive({
  id: 0,
  title: '',
  description: '',
  coverUrl: '',
  isPublic: 0
})
const coverTab = ref<'upload' | 'asset' | 'url'>('upload')
const assetImages = ref<AssetImageItem[]>([])

const editFormRules: FormRules = {
  title: [{ required: true, message: '请输入作品标题', trigger: 'blur' }]
}

async function openEditWork(work: WorkItem) {
  editingWork.value = work
  editForm.id = work.id
  editForm.title = decodeTitle(work.title)
  editForm.description = work.description || ''
  editForm.coverUrl = work.coverUrl || ''
  editForm.isPublic = work.isPublic ?? 0
  coverTab.value = 'upload'
  assetImages.value = []
  editDialogVisible.value = true

  // 加载资产图片
  if (work.taskId) {
    try {
      const images = await getAssetImagesByTask(work.taskId)
      assetImages.value = Array.isArray(images) ? images : []
    } catch (_) { /* 静默失败 */ }
  }
}

function beforeCoverUpload(): boolean {
  return true
}

async function handleCoverUpload(options: any) {
  const file: File = options.file
  if (!file) return
  try {
    const res = await uploadFile(file, editingWork.value?.taskId, 'work_cover')
    if (res?.fileUrl) {
      editForm.coverUrl = res.fileUrl
      ElMessage.success('封面上传成功')
    } else {
      ElMessage.error('上传失败')
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '上传失败')
  }
}

async function handleSubmitEdit() {
  if (!editFormRef.value) return
  await editFormRef.value.validate(async (valid) => {
    if (!valid) return
    editSubmitting.value = true
    try {
      await updateWork({
        id: editForm.id,
        title: editForm.title,
        description: editForm.description || undefined,
        coverUrl: editForm.coverUrl || undefined,
        isPublic: editForm.isPublic
      })
      ElMessage.success('保存成功')
      editDialogVisible.value = false
      loadData()
    } catch (e: any) {
      ElMessage.error(e?.message || '保存失败')
    } finally {
      editSubmitting.value = false
    }
  })
}

onMounted(loadData)
</script>

<style scoped>
.work-list {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.page-header h2 {
  margin: 0;
  font-size: 22px;
  font-weight: 800;
  color: var(--cd-text);
}

.work-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 20px;
}

.empty-state {
  grid-column: 1 / -1;
  text-align: center;
  padding: 60px 20px;
  color: var(--cd-text-secondary);
}
.empty-icon {
  font-size: 64px;
  margin-bottom: 16px;
}
.empty-state p {
  margin: 0 0 20px;
  font-size: 14px;
}

.work-card {
  overflow: hidden;
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}
.work-card:hover {
  transform: translateY(-4px);
  box-shadow: 4px 4px 0 0 var(--cd-shadow);
}

.cover-wrapper {
  position: relative;
  width: 100%;
  aspect-ratio: 16 / 9;
  background-color: var(--cd-bg-soft);
  overflow: hidden;
  cursor: pointer;
}
.cover {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.2s ease;
}
.work-card:hover .cover {
  transform: scale(1.03);
}
.cover-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--cd-text-secondary);
}
.cover-overlay {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background-color: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  opacity: 0;
  transition: opacity 0.2s ease;
}
.work-card:hover .cover-overlay {
  opacity: 1;
}
.status-tag {
  position: absolute;
  top: 12px;
  right: 12px;
}
.work-card-actions {
  position: absolute;
  left: 12px;
  bottom: 12px;
  background: rgba(0, 0, 0, 0.6);
  border-radius: 6px;
  padding: 2px 8px;
  backdrop-filter: blur(4px);
  display: flex;
  gap: 6px;
}
.work-card-actions :deep(.el-button) {
  color: #fff !important;
}

.work-info {
  padding: 14px 16px 16px;
  cursor: pointer;
}
.work-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--cd-text);
  margin-bottom: 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.work-meta {
  display: flex;
  gap: 14px;
  margin-bottom: 6px;
}
.meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--cd-text-secondary);
}
.work-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.work-time {
  font-size: 12px;
  color: var(--cd-text-secondary);
}
.task-link {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: 12px;
}

.pagination {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}

/* 视频弹窗 */
.player-dialog-content {
  width: 100%;
}
.video-and-list-wrapper {
  display: flex;
  gap: 14px;
  align-items: flex-start;
}
.video-main-area {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.dialog-video {
  width: 100%;
  max-height: 500px;
  border-radius: 8px;
  background: #000;
  display: block;
}
.segment-control-bar {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14px;
  padding: 8px 16px;
  border: 1.5px solid var(--cd-border);
  border-radius: 8px;
  background: var(--cd-bg-soft);
  color: var(--cd-text);
  flex-wrap: wrap;
}
.seg-btn {
  font-size: 20px;
  color: var(--cd-primary);
  cursor: pointer;
}
.seg-btn.disabled {
  color: var(--cd-text-secondary);
  opacity: 0.4;
  cursor: not-allowed;
}
.seg-info {
  font-weight: 600;
  font-size: 13px;
}
.segment-list-area {
  width: 240px;
  flex-shrink: 0;
  max-height: 540px;
  display: flex;
  flex-direction: column;
  border: 1.5px solid var(--cd-border);
  border-radius: 10px;
  background: var(--cd-bg-card);
  overflow: hidden;
}
.seg-list-title {
  padding: 10px 12px;
  font-size: 13px;
  font-weight: 700;
  border-bottom: 1.5px solid var(--cd-border);
  color: var(--cd-text);
  background: var(--cd-bg-soft);
}
.seg-list-scroll {
  overflow-y: auto;
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.seg-item {
  display: flex;
  gap: 8px;
  padding: 6px;
  border-radius: 8px;
  cursor: pointer;
  border: 2px solid transparent;
  transition: all 0.12s;
}
.seg-item:hover {
  background: var(--cd-bg-soft);
}
.seg-item.active {
  background: var(--cd-bg-soft);
  border-color: var(--cd-primary);
}
.seg-thumb {
  position: relative;
  width: 72px;
  height: 42px;
  border-radius: 6px;
  overflow: hidden;
  background: #000;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
}
.seg-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.seg-index-label {
  position: absolute;
  left: 2px;
  bottom: 2px;
  font-size: 10px;
  padding: 1px 4px;
  background: rgba(0, 0, 0, 0.6);
  border-radius: 3px;
}
.seg-meta {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 2px;
  min-width: 0;
}
.seg-name {
  font-size: 12px;
  font-weight: 700;
  color: var(--cd-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.seg-sub {
  font-size: 11px;
  color: var(--cd-text-secondary);
}
.player-dialog-empty {
  width: 100%;
  min-height: 300px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 40px 20px;
  color: var(--cd-text-secondary);
}
.empty-hint {
  font-size: 12px;
  margin: 0;
  text-align: center;
}
.empty-actions {
  display: flex;
  gap: 10px;
  justify-content: center;
  flex-wrap: wrap;
  margin-top: 12px;
}
.empty-actions .sketch-btn {
  min-width: 160px;
  justify-content: center;
}

/* 分享弹窗 */
.share-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.share-work-info {
  display: flex;
  gap: 12px;
  padding: 12px;
  border: 1.5px solid var(--cd-border);
  border-radius: 8px;
}
.share-cover {
  width: 80px;
  height: 48px;
  object-fit: cover;
  border-radius: 6px;
}
.share-work-meta {
  flex: 1;
}
.share-title {
  font-weight: 700;
  color: var(--cd-text);
  margin-bottom: 4px;
}
.share-desc {
  font-size: 12px;
  color: var(--cd-text-secondary);
}
.share-link-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.share-label {
  font-size: 12px;
  color: var(--cd-text-secondary);
  font-weight: 600;
}
.share-link-box {
  width: 100%;
}
.share-link-box :deep(.el-input__wrapper) {
  background-color: var(--cd-bg-soft);
}
.share-actions {
  display: flex;
  justify-content: center;
}
.share-expire {
  font-size: 12px;
  color: var(--cd-text-secondary);
  text-align: center;
}
.share-generating {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 30px;
  color: var(--cd-text-secondary);
}
.spin-icon {
  animation: spin 1s linear infinite;
}
@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* 封面选择器 */
.cover-selector {
  width: 100%;
}
.cover-preview {
  position: relative;
  width: 120px;
  height: 72px;
  border-radius: 8px;
  overflow: hidden;
  border: 1.5px solid var(--cd-border);
  margin-bottom: 12px;
}
.preview-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.remove-cover {
  position: absolute;
  top: 4px;
  right: 4px;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.6);
  border: none;
  color: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
}
.remove-cover:hover {
  background: rgba(0, 0, 0, 0.8);
}
.cover-preview-placeholder {
  width: 120px;
  height: 72px;
  border-radius: 8px;
  border: 1.5px dashed var(--cd-border);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  color: var(--cd-text-secondary);
  font-size: 12px;
  margin-bottom: 12px;
}

.cover-tabs {
  width: 100%;
}
.cover-tabs :deep(.el-tabs__nav) {
  gap: 4px;
}
.cover-tabs :deep(.el-tabs__item) {
  font-size: 12px;
  height: 34px;
}

.cover-uploader {
  width: 100%;
}
.uploader-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 16px;
  border: 1.5px dashed var(--cd-border);
  border-radius: 8px;
  cursor: pointer;
  color: var(--cd-text-secondary);
  transition: all 0.15s;
  font-size: 12px;
}
.uploader-btn:hover {
  border-color: var(--cd-primary);
  color: var(--cd-primary);
}

.no-assets {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 16px;
  color: var(--cd-text-secondary);
  font-size: 12px;
}
.asset-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
}
.asset-item {
  position: relative;
  border-radius: 6px;
  overflow: hidden;
  border: 2px solid transparent;
  cursor: pointer;
  transition: border-color 0.15s;
}
.asset-item:hover {
  border-color: var(--cd-primary);
}
.asset-item.active {
  border-color: var(--cd-primary);
  box-shadow: 0 0 0 2px var(--cd-primary);
}
.asset-item img {
  width: 100%;
  aspect-ratio: 1;
  object-fit: cover;
  display: block;
}
.asset-name {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 2px 4px;
  background: rgba(0, 0, 0, 0.6);
  color: #fff;
  font-size: 10px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

@media (max-width: 768px) {
  .work-grid {
    grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
    gap: 14px;
  }
  .asset-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}
</style>
