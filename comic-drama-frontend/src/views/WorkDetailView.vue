<template>
  <div class="work-detail" v-loading="loading">
    <div v-if="work" class="detail-content">
      <div class="detail-header">
        <button class="back-btn" @click="router.back()">
          <el-icon><ArrowLeft /></el-icon>
          返回
        </button>
        <h2>{{ decodeTitle(work.title) || '未命名作品' }}</h2>
        <el-tag type="success" effect="light" round>
          已完成
        </el-tag>
        <div class="header-actions">
          <button class="sketch-btn sketch-btn--ghost btn-sm" @click="openShare">
            <el-icon><Share /></el-icon>
            分享
          </button>
          <button
            v-if="canViewTask"
            class="sketch-btn sketch-btn--ghost btn-sm"
            @click="goToTask"
          >
            <el-icon><Link /></el-icon>
            查看任务
          </button>
          <button class="sketch-btn sketch-btn--ghost btn-sm" @click="openEditWork">
            <el-icon><Edit /></el-icon>
            编辑作品
          </button>
        </div>
      </div>

      <div class="player-section sketch-card">
        <div class="player-wrapper">
          <div v-if="playableUrl || isZipOnly" class="player-container">
            <video
              v-if="playableUrl"
              ref="videoRef"
              :src="currentVideoSrc"
              :poster="work.coverUrl"
              controls
              class="video-player"
              @ended="handleVideoEnded"
              @loadedmetadata="handleVideoMeta"
            >
              您的浏览器不支持视频播放
            </video>
            <div v-else class="zip-placeholder">
              <el-icon :size="80" class="zip-icon"><FolderOpened /></el-icon>
              <h3>作品已打包为 ZIP 成片</h3>
              <p class="zip-hint">
                因作品包含多段分镜视频，无法直接在网页播放。<br />
                请点击下方按钮下载完整成片包，或使用时间线分段播放。
              </p>
              <div class="zip-buttons">
                <button v-if="work.zipUrl" class="sketch-btn sketch-btn--primary" @click="handleDownloadZip">
                  <el-icon><Download /></el-icon>
                  下载成片 ZIP（{{ formatFileSize(work.fileSize) }}）
                </button>
                <button v-if="timeline.length > 0" class="sketch-btn sketch-btn--ghost" @click="seekToTimeline(0)">
                  <el-icon><VideoPlay /></el-icon>
                  从第 1 段开始播放
                </button>
              </div>
            </div>
          </div>
          <div v-else class="player-placeholder">
            <el-icon :size="64"><VideoPlay /></el-icon>
            <p>视频资源未就绪</p>
            <p class="placeholder-hint">请确认任务已完成视频合并步骤</p>
          </div>

          <div v-if="timeline.length > 1" class="segment-bar">
            <el-icon class="seg-nav" :class="{ disabled: currentPlayIndex <= 0 }" @click="playPrev"><CaretLeft /></el-icon>
            <span class="seg-info">
              第 {{ currentPlayIndex + 1 }} / {{ timeline.length }} 段
              <span v-if="currentSegment"> · 时长 {{ formatDuration(currentSegment.duration || 0) }}</span>
            </span>
            <el-icon class="seg-nav" :class="{ disabled: currentPlayIndex >= timeline.length - 1 }" @click="playNext"><CaretRight /></el-icon>
            <el-switch
              v-model="autoPlayNext"
              inline-prompt
              active-text="自动连播"
              inactive-text="单段播放"
              style="margin-left: 12px"
            />
          </div>
        </div>

        <div class="work-meta-bar">
          <div class="meta-item">
            <span class="meta-label">总时长</span>
            <span class="meta-value">{{ formatDuration(work.duration) }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-label">分辨率</span>
            <span class="meta-value">{{ work.resolution || '-' }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-label">画幅</span>
            <span class="meta-value">{{ work.aspectRatio || '-' }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-label">文件大小</span>
            <span class="meta-value">{{ formatFileSize(work.fileSize) }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-label">浏览</span>
            <span class="meta-value">{{ work.viewCount || 0 }} 次</span>
          </div>
          <div class="meta-item">
            <span class="meta-label">分镜段数</span>
            <span class="meta-value">{{ effectiveSegmentCount }} 段</span>
          </div>
          <div class="meta-item meta-actions">
            <button v-if="work.zipUrl" class="download-btn" @click="handleDownloadZip" title="下载完整成片包">
              <el-icon><Download /></el-icon>
              下载成片
            </button>
          </div>
        </div>
      </div>

      <div class="timeline-section sketch-card">
        <div class="section-header">
          <h3>分镜时间线</h3>
          <div class="section-actions">
            <span class="section-hint">共 {{ timeline.length }} 个分镜 · 点击任意分镜跳转播放</span>
          </div>
        </div>
        <div class="timeline-track">
          <div
            v-for="(item, index) in timeline"
            :key="item.id"
            class="timeline-item"
            :class="{ active: currentPlayIndex === index }"
          >
            <div class="timeline-thumb" @click="seekToTimeline(index)">
              <div class="thumb-index">{{ index + 1 }}</div>
              <span class="thumb-duration">{{ formatDuration(item.duration || 0) }}</span>
              <div v-if="currentPlayIndex === index" class="playing-indicator">
                <span></span><span></span><span></span>
              </div>
            </div>
          </div>
          <div v-if="timeline.length === 0" class="empty-timeline">
            <el-icon :size="36" class="empty-icon"><Film /></el-icon>
            <p>暂无分镜时间线</p>
            <p class="empty-hint">任务重新执行视频合并步骤后，会自动写入分镜条目</p>
          </div>
        </div>
      </div>

      <div v-if="work.description" class="description-section sketch-card">
        <div class="section-header">
          <h3>作品描述</h3>
        </div>
        <p class="description-text">{{ work.description }}</p>
      </div>

      <div class="actions-section">
        <button class="sketch-btn sketch-btn--ghost" @click="router.back()">
          <el-icon><ArrowLeft /></el-icon>
          返回列表
        </button>
        <button v-if="work.zipUrl" class="sketch-btn sketch-btn--primary" @click="handleDownloadZip">
          <el-icon><Download /></el-icon>
          下载成片包
        </button>
      </div>
    </div>

    <!-- 编辑作品对话框 -->
    <el-dialog
      v-model="editWorkDialogVisible"
      title="编辑作品信息"
      width="560px"
      :close-on-click-modal="false"
    >
      <el-form ref="editWorkFormRef" :model="editWorkForm" :rules="editWorkFormRules" label-width="100px">
        <el-form-item label="作品标题" prop="title">
          <el-input v-model="editWorkForm.title" placeholder="请输入标题" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="作品描述">
          <el-input v-model="editWorkForm.description" type="textarea" :rows="3" placeholder="选填" />
        </el-form-item>

        <el-form-item label="作品封面">
          <div class="cover-selector">
            <div v-if="editWorkForm.coverUrl" class="cover-preview">
              <img :src="editWorkForm.coverUrl" class="preview-img" />
              <button class="remove-cover" @click="editWorkForm.coverUrl = ''">
                <el-icon><Close /></el-icon>
              </button>
            </div>
            <div v-else class="cover-preview-placeholder">
              <el-icon :size="36"><Picture /></el-icon>
              <span>选择封面</span>
            </div>

            <el-tabs v-model="coverTab" class="cover-tabs">
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
                    :class="{ active: editWorkForm.coverUrl === img.imageUrl }"
                    @click="editWorkForm.coverUrl = img.imageUrl"
                  >
                    <img :src="img.thumbnailUrl || img.imageUrl" :alt="img.assetName || '资产'" />
                    <span class="asset-name">{{ img.assetName || '资产' }}</span>
                  </div>
                </div>
              </el-tab-pane>
            </el-tabs>
          </div>
        </el-form-item>

        <el-form-item label="是否公开">
          <el-radio-group v-model="editWorkForm.isPublic">
            <el-radio :value="1">公开</el-radio>
            <el-radio :value="0">不公开</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <button class="sketch-btn sketch-btn--ghost" @click="editWorkDialogVisible = false">取消</button>
        <button class="sketch-btn" :disabled="editWorkSubmitting" @click="handleSubmitEditWork">
          {{ editWorkSubmitting ? '保存中...' : '保存' }}
        </button>
      </template>
    </el-dialog>

    <!-- 分享弹窗 -->
    <el-dialog
      v-model="shareVisible"
      title="分享作品"
      width="460px"
      :close-on-click-modal="true"
    >
      <div v-if="work" class="share-content">
        <div class="share-work-info">
          <img v-if="work.coverUrl" :src="work.coverUrl" class="share-cover" />
          <div class="share-work-meta">
            <div class="share-title">{{ decodeTitle(work.title) }}</div>
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
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import {
  ArrowLeft, VideoPlay, Edit, Share, Link, Download, FolderOpened,
  CaretLeft, CaretRight, Film, Close, Picture, UploadFilled, Loading,
  CopyDocument
} from '@element-plus/icons-vue'
import {
  getWork,
  getWorkTimeline,
  updateWork,
  getAssetImagesByTask,
  uploadFile,
  generateWorkShareToken,
  type WorkItem,
  type WorkTimelineItem,
  type AssetImageItem
} from '@/api/work'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const workId = computed(() => Number(route.params.id))
const work = ref<WorkItem | null>(null)
const timeline = ref<WorkTimelineItem[]>([])
const currentPlayIndex = ref(0)
const autoPlayNext = ref(true)
const videoRef = ref<HTMLVideoElement>()

const canViewTask = computed(() => {
  if (!work.value?.taskId) return false
  const currentUserId = userStore.userInfo?.id
  if (!currentUserId) return false
  return work.value.userId === currentUserId || work.value.isPublic === 1
})

const effectiveSegmentCount = computed(() => {
  if (work.value?.segmentCount && work.value.segmentCount > 0) return work.value.segmentCount
  if (timeline.value.length > 0) return timeline.value.length
  return 0
})

const currentSegment = computed(() => timeline.value[currentPlayIndex.value])

const isZipOnly = computed(() => {
  const video = work.value?.videoUrl
  if (video && /\.(zip|ZIP)$/.test(video.split('?')[0])) return true
  if (!video && work.value?.zipUrl) return true
  return false
})

const playableUrl = computed(() => {
  if (timeline.value[currentPlayIndex.value]?.videoUrl) {
    return timeline.value[currentPlayIndex.value].videoUrl
  }
  const video = work.value?.videoUrl
  if (video && !/\.(zip|ZIP)$/.test(video.split('?')[0])) {
    return video
  }
  return ''
})

const currentVideoSrc = ref('')
watch(playableUrl, (url) => {
  currentVideoSrc.value = url
}, { immediate: true })

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

function goToTask() {
  if (work.value?.taskId) {
    router.push(`/task/${work.value.taskId}`)
  }
}

function handleDownloadZip() {
  const url = work.value?.zipUrl
  if (!url) {
    ElMessage.warning('暂无完整成片包可供下载')
    return
  }
  const a = document.createElement('a')
  a.href = url
  a.target = '_blank'
  a.rel = 'noopener noreferrer'
  const title = decodeTitle(work.value?.title) || '作品'
  a.download = `${title}.zip`
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  ElMessage.success('已开始下载成片包')
}

function handleVideoEnded() {
  if (autoPlayNext.value && currentPlayIndex.value < timeline.value.length - 1) {
    playNext()
  }
}

function handleVideoMeta() {
  // 预留
}

function playPrev() {
  if (currentPlayIndex.value > 0) seekToTimeline(currentPlayIndex.value - 1)
}
function playNext() {
  if (currentPlayIndex.value < timeline.value.length - 1) seekToTimeline(currentPlayIndex.value + 1)
}

async function loadWork() {
  loading.value = true
  try {
    const [w, t] = await Promise.all([
      getWork(workId.value).catch(() => null),
      getWorkTimeline(workId.value).catch(() => [] as WorkTimelineItem[])
    ])
    if (w) {
      work.value = { ...w, title: decodeTitle(w.title) }
      timeline.value = Array.isArray(t) ? [...t].sort((a, b) => (a.orderIndex ?? 0) - (b.orderIndex ?? 0)) : []
      currentPlayIndex.value = 0
    } else {
      work.value = null
      timeline.value = []
    }
  } catch (e) {
    console.error('Failed to load work', e)
    work.value = null
  } finally {
    loading.value = false
  }
}

function seekToTimeline(index: number) {
  const item = timeline.value[index]
  if (!item?.videoUrl) {
    ElMessage.info('该段暂无视频地址')
  }
  currentPlayIndex.value = index
  currentVideoSrc.value = timeline.value[index]?.videoUrl || work.value?.videoUrl || ''
  if (videoRef.value) {
    videoRef.value.load()
    videoRef.value.play().catch(() => { /* 自动播放被浏览器拦截时忽略 */ })
  }
}

// ===== 编辑作品 =====
const editWorkDialogVisible = ref(false)
const editWorkSubmitting = ref(false)
const editWorkFormRef = ref<FormInstance>()
const coverTab = ref<'upload' | 'asset'>('upload')
const assetImages = ref<AssetImageItem[]>([])
const editWorkForm = reactive({
  id: 0,
  title: '',
  description: '',
  coverUrl: '',
  isPublic: 0
})
const editWorkFormRules: FormRules = {
  title: [{ required: true, message: '请输入作品标题', trigger: 'blur' }]
}

async function openEditWork() {
  if (!work.value) return
  editWorkForm.id = work.value.id
  editWorkForm.title = decodeTitle(work.value.title)
  editWorkForm.description = work.value.description || ''
  editWorkForm.coverUrl = work.value.coverUrl || ''
  editWorkForm.isPublic = work.value.isPublic ?? 0
  coverTab.value = 'upload'
  assetImages.value = []
  editWorkDialogVisible.value = true

  if (work.value.taskId) {
    try {
      const images = await getAssetImagesByTask(work.value.taskId)
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
    const res = await uploadFile(file, work.value?.taskId, 'work_cover')
    if (res?.fileUrl) {
      editWorkForm.coverUrl = res.fileUrl
      ElMessage.success('封面上传成功')
    } else {
      ElMessage.error('上传失败')
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '上传失败')
  }
}

async function handleSubmitEditWork() {
  if (!editWorkFormRef.value) return
  await editWorkFormRef.value.validate(async (valid) => {
    if (!valid) return
    editWorkSubmitting.value = true
    try {
      await updateWork({
        id: editWorkForm.id,
        title: editWorkForm.title,
        description: editWorkForm.description || undefined,
        coverUrl: editWorkForm.coverUrl || undefined,
        isPublic: editWorkForm.isPublic
      })
      ElMessage.success('保存成功')
      editWorkDialogVisible.value = false
      await loadWork()
    } catch (e: any) {
      ElMessage.error(e?.message || '保存失败')
    } finally {
      editWorkSubmitting.value = false
    }
  })
}

// ===== 分享 =====
const shareVisible = ref(false)
const shareUrl = ref('')

async function openShare() {
  shareUrl.value = ''
  shareVisible.value = true
  if (!work.value) return
  try {
    const token = await generateWorkShareToken(work.value.id, 72)
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
.header-actions {
  margin-left: auto;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.btn-sm {
  padding: 5px 10px;
  font-size: 12px;
}

.player-section {
  padding: 0;
  overflow: hidden;
}
.player-wrapper {
  background-color: #000;
  width: 100%;
}
.player-container {
  width: 100%;
  position: relative;
  max-height: 540px;
  min-height: 240px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(180deg, #0a0a0a 0%, #161616 100%);
}
.video-player {
  width: 100%;
  max-height: 540px;
  display: block;
  background: #000;
}
.zip-placeholder {
  text-align: center;
  padding: 48px 24px;
  color: #fff;
  width: 100%;
}
.zip-icon {
  color: var(--cd-primary);
  margin-bottom: 12px;
}
.zip-placeholder h3 {
  margin: 0 0 8px;
  font-size: 20px;
  font-weight: 800;
}
.zip-hint {
  font-size: 14px;
  opacity: 0.75;
  line-height: 1.8;
  margin: 0 0 20px;
}
.zip-buttons {
  display: flex;
  justify-content: center;
  flex-wrap: wrap;
  gap: 10px;
}

.player-placeholder {
  height: 300px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: var(--cd-text-secondary);
}
.placeholder-hint {
  font-size: 12px;
  opacity: 0.7;
  margin: 0;
}

.segment-bar {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 10px 20px;
  background-color: var(--cd-bg-soft);
  border-top: 1.5px solid var(--cd-border);
  border-bottom: 1.5px solid var(--cd-border);
  color: var(--cd-text);
  font-size: 13px;
  flex-wrap: wrap;
}
.seg-nav {
  font-size: 20px;
  cursor: pointer;
  color: var(--cd-primary);
}
.seg-nav.disabled {
  color: var(--cd-text-secondary);
  opacity: 0.5;
  cursor: not-allowed;
}
.seg-info {
  font-weight: 600;
}

.work-meta-bar {
  display: flex;
  padding: 16px 20px;
  background-color: var(--cd-bg-card);
  gap: 28px;
  flex-wrap: wrap;
  align-items: center;
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
.meta-actions {
  margin-left: auto;
}
.download-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border: 2px solid var(--cd-primary);
  border-radius: 8px;
  background: transparent;
  color: var(--cd-primary);
  font-weight: 700;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s ease;
}
.download-btn:hover {
  background-color: var(--cd-primary);
  color: #fff;
}

.timeline-section,
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
  flex-wrap: wrap;
}
.section-hint {
  font-size: 13px;
  color: var(--cd-text-secondary);
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
  position: relative;
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
.playing-indicator {
  position: absolute;
  bottom: 4px;
  display: flex;
  gap: 3px;
}
.playing-indicator span {
  width: 3px;
  height: 10px;
  background-color: var(--cd-primary);
  border-radius: 2px;
  animation: bounce 0.9s infinite ease-in-out;
}
.playing-indicator span:nth-child(2) { animation-delay: 0.15s; }
.playing-indicator span:nth-child(3) { animation-delay: 0.3s; }
@keyframes bounce {
  0%, 100% { transform: scaleY(0.4); }
  50% { transform: scaleY(1); }
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
.empty-icon {
  margin-bottom: 8px;
  opacity: 0.6;
}
.empty-timeline p {
  margin: 4px 0;
}
.empty-hint {
  font-size: 12px;
  opacity: 0.7;
}

.description-text {
  margin: 0;
  font-size: 14px;
  line-height: 1.8;
  color: var(--cd-text);
  white-space: pre-wrap;
}

.actions-section {
  display: flex;
  gap: 12px;
  justify-content: center;
  padding: 12px 0;
  flex-wrap: wrap;
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

@media (max-width: 768px) {
  .work-meta-bar {
    gap: 20px;
  }
  .meta-actions {
    width: 100%;
    justify-content: center;
    margin-left: 0;
  }
  .download-btn {
    width: 100%;
    justify-content: center;
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
  .asset-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}
</style>
