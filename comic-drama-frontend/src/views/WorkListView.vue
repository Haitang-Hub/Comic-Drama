<template>
  <div class="work-list">
    <div class="page-header">
      <h2>我的作品</h2>
      <div class="header-actions">
        <el-input
          v-model="query.keyword"
          placeholder="搜索作品标题"
          :prefix-icon="Search"
          clearable
          class="search-input"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-select v-model="query.status" placeholder="全部状态" clearable class="status-select" @change="handleSearch">
          <el-option label="已发布" :value="1" />
          <el-option label="草稿" :value="0" />
        </el-select>
        <button class="sketch-btn sketch-btn--primary" @click="openCreateWork">
          <el-icon><Plus /></el-icon>
          手动创建作品
        </button>
      </div>
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
        <div class="cover-wrapper" @click="goDetail(work)">
          <img
            v-if="work.coverUrl"
            :src="work.coverUrl"
            :alt="work.title"
            class="cover"
          />
          <div v-else class="cover-placeholder">
            <el-icon :size="48"><Film /></el-icon>
          </div>
          <div class="cover-overlay">
            <el-icon :size="24"><VideoPlay /></el-icon>
          </div>
          <el-tag v-if="work.status === 1" type="success" effect="dark" class="status-tag" round>
            已发布
          </el-tag>
          <el-tag v-else type="info" effect="dark" class="status-tag" round>
            草稿
          </el-tag>
          <div class="work-card-actions" @click.stop>
            <el-button link type="primary" size="small" @click="openEditWork(work)">编辑</el-button>
          </div>
        </div>
        <div class="work-info" @click="goDetail(work)">
          <div class="work-title">{{ work.title || '未命名作品' }}</div>
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
          <div class="work-time">{{ formatTime(work.createTime) }}</div>
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

    <el-dialog
      v-model="createDialogVisible"
      title="手动创建作品"
      width="500px"
      :close-on-click-modal="false"
    >
      <el-form ref="createFormRef" :model="createForm" :rules="createFormRules" label-width="90px">
        <el-form-item label="作品标题" prop="title">
          <el-input v-model="createForm.title" placeholder="请输入标题" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="关联任务ID" prop="taskId">
          <el-input-number v-model="createForm.taskId" :min="0" :step="1" controls-position="right" style="width: 100%" />
          <div class="form-hint">可选，关联到某个生成任务</div>
        </el-form-item>
        <el-form-item label="作品描述">
          <el-input v-model="createForm.description" type="textarea" :rows="4" placeholder="选填，作品简介" />
        </el-form-item>
      </el-form>
      <template #footer>
        <button class="sketch-btn sketch-btn--ghost" @click="createDialogVisible = false">取消</button>
        <button class="sketch-btn" :disabled="createSubmitting" @click="handleSubmitCreate">
          {{ createSubmitting ? '创建中...' : '创建' }}
        </button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="editDialogVisible"
      title="编辑作品"
      width="520px"
      :close-on-click-modal="false"
    >
      <el-form ref="editFormRef" :model="editForm" :rules="editFormRules" label-width="100px">
        <el-form-item label="作品标题" prop="title">
          <el-input v-model="editForm.title" placeholder="请输入标题" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="作品描述">
          <el-input v-model="editForm.description" type="textarea" :rows="3" placeholder="选填" />
        </el-form-item>
        <el-form-item label="封面URL">
          <el-input v-model="editForm.coverUrl" placeholder="图片URL，选填" />
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
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Search, Plus, Film, VideoPlay, Clock, Monitor, View } from '@element-plus/icons-vue'
import { pageWork, createWork, updateWork, type WorkItem } from '@/api/work'

const router = useRouter()
const loading = ref(false)
const workList = ref<WorkItem[]>([])
const total = ref(0)

const query = reactive({
  page: 1,
  size: 12,
  keyword: '',
  status: undefined as number | undefined
})

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
    workList.value = res.records || []
    total.value = res.total || 0
  } catch (e) {
    console.error('Failed to load works', e)
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

function goDetail(work: WorkItem) {
  router.push(`/work/${work.id}`)
}

// ===== 创建作品 =====
const createDialogVisible = ref(false)
const createSubmitting = ref(false)
const createFormRef = ref<FormInstance>()
const createForm = reactive({
  title: '',
  taskId: 0,
  description: ''
})
const createFormRules: FormRules = {
  title: [{ required: true, message: '请输入作品标题', trigger: 'blur' }]
}

function openCreateWork() {
  Object.assign(createForm, { title: '', taskId: 0, description: '' })
  createDialogVisible.value = true
}

async function handleSubmitCreate() {
  if (!createFormRef.value) return
  await createFormRef.value.validate(async (valid) => {
    if (!valid) return
    createSubmitting.value = true
    try {
      const payload: any = {
        title: createForm.title,
        description: createForm.description || undefined
      }
      if (createForm.taskId > 0) payload.taskId = createForm.taskId
      await createWork(payload)
      ElMessage.success('作品创建成功')
      createDialogVisible.value = false
      loadData()
    } catch (e: any) {
      ElMessage.error(e?.message || '创建失败')
    } finally {
      createSubmitting.value = false
    }
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
const editFormRules: FormRules = {
  title: [{ required: true, message: '请输入作品标题', trigger: 'blur' }]
}

function openEditWork(work: WorkItem) {
  editingWork.value = work
  Object.assign(editForm, {
    id: work.id,
    title: work.title,
    description: work.description || '',
    coverUrl: work.coverUrl || '',
    isPublic: work.isPublic ?? 0
  })
  editDialogVisible.value = true
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
      ElMessage.success('作品已更新')
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

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}
.page-header h2 {
  margin: 0;
  font-size: 22px;
  font-weight: 800;
  color: var(--cd-text);
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.search-input {
  width: 240px;
}
.status-select {
  width: 140px;
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
.work-time {
  font-size: 12px;
  color: var(--cd-text-secondary);
}

.pagination {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}

.form-hint {
  margin-top: 4px;
  font-size: 12px;
  color: var(--cd-text-secondary);
}

@media (max-width: 768px) {
  .header-actions {
    width: 100%;
    flex-direction: column;
  }
  .search-input,
  .status-select {
    width: 100%;
  }
}
</style>
