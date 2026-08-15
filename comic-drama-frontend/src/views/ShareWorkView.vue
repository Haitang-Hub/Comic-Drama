<template>
  <div class="share-view" v-loading="loading">
    <div v-if="work" class="share-content">
      <div class="share-header">
        <div class="badge">
          <el-icon><Share /></el-icon>
          作品分享
        </div>
        <div v-if="!tokenValid" class="expired-tip">
          <el-icon><Warning /></el-icon>
          该分享链接已失效或过期
        </div>
      </div>

      <div class="work-header">
        <h1>{{ decodeTitle(work.title) || '未命名作品' }}</h1>
        <div class="work-meta">
          <span class="meta-item">
            <el-icon><Clock /></el-icon>
            {{ formatDuration(work.duration) }}
          </span>
          <span class="meta-item" v-if="work.resolution">
            <el-icon><Monitor /></el-icon>
            {{ work.resolution }}
          </span>
          <span class="meta-item">
            <el-icon><View /></el-icon>
            {{ work.viewCount || 0 }} 次浏览
          </span>
        </div>
      </div>

      <div class="player-section">
        <div class="player-wrapper">
          <video
            v-if="playableUrl"
            ref="videoRef"
            :src="playableUrl"
            :poster="work.coverUrl"
            controls
            autoplay
            class="video-player"
          >
            您的浏览器不支持视频播放
          </video>
          <div v-else class="fallback">
            <img v-if="work.coverUrl" :src="work.coverUrl" class="fallback-cover" />
            <el-icon :size="48" class="fallback-icon"><VideoPlay /></el-icon>
            <p>视频资源暂无法直接播放</p>
            <button v-if="work.zipUrl" class="sketch-btn sketch-btn--primary" @click="downloadZip">
              <el-icon><Download /></el-icon>
              下载成片 ZIP
            </button>
          </div>
        </div>
      </div>

      <div v-if="work.description" class="description-section">
        <h3>作品描述</h3>
        <p>{{ work.description }}</p>
      </div>

      <div class="footer-actions">
        <button v-if="work.zipUrl" class="sketch-btn sketch-btn--primary" @click="downloadZip">
          <el-icon><Download /></el-icon>
          下载成片包
        </button>
        <button
          v-if="isLoggedIn"
          class="sketch-btn sketch-btn--ghost"
          @click="goToWorkDetail"
        >
          <el-icon><Link /></el-icon>
          在我的账户查看
        </button>
        <button
          v-else
          class="sketch-btn sketch-btn--ghost"
          @click="goLogin"
        >
          <el-icon><User /></el-icon>
          登录查看更多
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Share, Warning, Clock, Monitor, View, VideoPlay, Download, Link, User } from '@element-plus/icons-vue'
import { getWorkByShareToken, type WorkItem } from '@/api/work'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const work = ref<WorkItem | null>(null)
const tokenValid = ref(true)
const videoRef = ref<HTMLVideoElement>()

const token = computed(() => String(route.params.token || ''))
const isLoggedIn = computed(() => userStore.isLogin)

const playableUrl = computed(() => {
  const v = work.value?.videoUrl
  if (v && !/\.(zip|ZIP)$/.test(v.split('?')[0])) return v
  return ''
})

function decodeTitle(title?: string): string {
  if (!title) return ''
  try {
    const decoded = decodeURIComponent(title)
    if (decoded !== title && !/[\u4e00-\u9fa5]/.test(title)) return decoded
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

function downloadZip() {
  const url = work.value?.zipUrl
  if (!url) {
    ElMessage.warning('暂无成片包')
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
}

function goToWorkDetail() {
  if (!work.value) return
  router.push(`/work/${work.value.id}`)
}

function goLogin() {
  router.push({ path: '/login', query: { redirect: route.fullPath } })
}

async function loadWork() {
  if (!token.value) {
    tokenValid.value = false
    return
  }
  loading.value = true
  try {
    const w = await getWorkByShareToken(token.value)
    if (w) {
      work.value = { ...w, title: decodeTitle(w.title) }
      tokenValid.value = true
    } else {
      work.value = null
      tokenValid.value = false
    }
  } catch (e) {
    console.error('Failed to load shared work', e)
    work.value = null
    tokenValid.value = false
  } finally {
    loading.value = false
  }
}

onMounted(loadWork)
</script>

<style scoped>
.share-view {
  min-height: calc(100vh - 140px);
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 24px 16px;
}

.share-content {
  width: 100%;
  max-width: 860px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.share-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 12px;
  background: var(--cd-primary);
  color: #fff;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 600;
}
.expired-tip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #f56c6c;
  font-size: 12px;
  font-weight: 600;
}

.work-header {
  display: flex;
  flex-direction: column;
  gap: 10px;
  text-align: center;
}
.work-header h1 {
  margin: 0;
  font-size: 24px;
  font-weight: 800;
  color: var(--cd-text);
}
.work-meta {
  display: flex;
  justify-content: center;
  gap: 18px;
  flex-wrap: wrap;
}
.meta-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: var(--cd-text-secondary);
}

.player-section {
  width: 100%;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.12);
}
.player-wrapper {
  background: #000;
  width: 100%;
  position: relative;
}
.video-player {
  width: 100%;
  max-height: 520px;
  display: block;
  background: #000;
}
.fallback {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 60px 24px;
  color: #fff;
  min-height: 280px;
}
.fallback-cover {
  max-width: 100%;
  max-height: 200px;
  object-fit: contain;
  border-radius: 8px;
}
.fallback-icon {
  color: rgba(255, 255, 255, 0.5);
}
.fallback p {
  margin: 0;
  color: rgba(255, 255, 255, 0.7);
}

.description-section {
  padding: 16px 20px;
  border: 1.5px solid var(--cd-border);
  border-radius: 10px;
  background-color: var(--cd-bg-card);
}
.description-section h3 {
  margin: 0 0 10px;
  font-size: 15px;
  font-weight: 700;
  color: var(--cd-text);
}
.description-section p {
  margin: 0;
  font-size: 14px;
  line-height: 1.7;
  color: var(--cd-text);
  white-space: pre-wrap;
}

.footer-actions {
  display: flex;
  justify-content: center;
  gap: 12px;
  flex-wrap: wrap;
  padding-top: 8px;
}

.sketch-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 10px 20px;
  border-radius: 8px;
  font-weight: 600;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.15s;
  border: 2px solid var(--cd-primary);
  background: var(--cd-primary);
  color: #fff;
}
.sketch-btn:hover {
  opacity: 0.9;
}
.sketch-btn--primary {
  background: var(--cd-primary);
  border-color: var(--cd-primary);
  color: #fff;
}
.sketch-btn--ghost {
  background: transparent;
  border-color: var(--cd-border);
  color: var(--cd-text);
}
.sketch-btn--ghost:hover {
  background: var(--cd-bg-soft);
}

@media (max-width: 600px) {
  .work-header h1 {
    font-size: 20px;
  }
  .work-meta {
    gap: 12px;
  }
  .sketch-btn {
    padding: 8px 14px;
    font-size: 13px;
  }
}
</style>
