<template>
  <div class="resource-center">
    <el-tabs v-model="activeSubTab" class="sub-tabs">
      <el-tab-pane label="资源文件" name="files">
        <div class="filter-bar sketch-card">
          <div class="filter-left">
            <el-input
              v-model="resQuery.file_name"
              placeholder="搜索文件名"
              :prefix-icon="Search"
              clearable
              class="search-input"
              @keyup.enter="loadResources"
              @clear="loadResources"
            />
            <el-select
              v-model="resQuery.file_type"
              placeholder="文件类型"
              clearable
              class="status-select"
              @change="loadResources"
            >
              <el-option label="图片" value="1" />
              <el-option label="视频" value="3" />
              <el-option label="音频" value="2" />
              <el-option label="文档" value="4" />
              <el-option label="其他" value="5" />
            </el-select>
            <el-input
              v-model="resQuery.task_id"
              placeholder="任务ID"
              clearable
              class="id-input"
              @keyup.enter="loadResources"
              @clear="loadResources"
            />
            <el-button :icon="Refresh" @click="loadResources">刷新</el-button>
          </div>
          <div class="filter-right">
            <el-radio-group v-model="viewMode" size="small" @change="loadResources">
              <el-radio-button label="table">列表</el-radio-button>
              <el-radio-button label="card">卡片</el-radio-button>
            </el-radio-group>
          </div>
        </div>

        <!-- 列表模式 -->
        <div class="table-card sketch-card" v-loading="resLoading" v-if="viewMode === 'table'">
          <el-table :data="resList" style="width: 100%" row-key="id" stripe>
            <el-table-column label="文件名" min-width="220" prop="fileName" show-overflow-tooltip />
            <el-table-column label="类型" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="fileTypeTag(row.fileType)" effect="light" round size="small">
                  {{ fileTypeLabel(row.fileType) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="大小" width="100" align="right">
              <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
            </el-table-column>
            <el-table-column label="任务ID" width="110" align="center" prop="taskId">
              <template #default="{ row }">
                <span v-if="row.taskId" class="task-id-tag">{{ row.taskId }}</span>
                <span v-else class="text-muted">—</span>
              </template>
            </el-table-column>
            <el-table-column label="上传时间" width="160">
              <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="180" fixed="right" align="center">
              <template #default="{ row }">
                <div class="action-btns">
                  <el-button link type="primary" size="small" @click="openSignUrl(row)">签名URL</el-button>
                  <el-button link type="danger" size="small" @click="handleDeleteResource(row)">删除</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>

          <div class="pagination">
            <el-pagination
              v-model:current-page="resQuery.page"
              v-model:page-size="resQuery.size"
              :total="resTotal"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next, jumper"
              background
              @current-change="loadResources"
              @size-change="handleResSizeChange"
            />
          </div>
        </div>

        <!-- 卡片模式 -->
        <div class="card-grid" v-loading="resLoading" v-else>
          <div
            v-for="item in resList"
            :key="item.id"
            class="resource-card sketch-card"
            @mouseenter="hoverCard = item.id"
            @mouseleave="hoverCard = null"
          >
            <div class="card-preview">
              <img
                v-if="isImage(item.fileType)"
                :src="item.fileUrl || item.tempUrl"
                :alt="item.fileName"
                class="preview-img"
                @error="onImgError"
              />
              <video
                v-else-if="isVideo(item.fileType)"
                :src="item.fileUrl || item.tempUrl"
                class="preview-img"
                muted
                preload="metadata"
              />
              <div v-else class="preview-icon">
                <el-icon :size="40"><Document /></el-icon>
              </div>
              <div class="card-overlay" v-if="hoverCard === item.id">
                <el-button size="small" type="primary" @click="openSignUrl(item)">签名URL</el-button>
                <el-button size="small" type="danger" @click="handleDeleteResource(item)">删除</el-button>
              </div>
            </div>
            <div class="card-body">
              <div class="card-name" :title="item.fileName">{{ item.fileName }}</div>
              <div class="card-meta">
                <el-tag :type="fileTypeTag(item.fileType)" size="small" effect="plain">
                  {{ fileTypeLabel(item.fileType) }}
                </el-tag>
                <span class="card-size">{{ formatSize(item.fileSize) }}</span>
              </div>
              <div class="card-footer">
                <span v-if="item.taskId" class="card-task">任务 {{ item.taskId }}</span>
                <span class="card-time">{{ fmtTime(item.createTime) }}</span>
              </div>
            </div>
          </div>
          <div v-if="!resLoading && resList.length === 0" class="empty-state">
            <el-empty description="暂无资源文件" />
          </div>
        </div>

        <div class="pagination" v-if="viewMode === 'table' && resTotal > 0">
          <el-pagination
            v-model:current-page="resQuery.page"
            v-model:page-size="resQuery.size"
            :total="resTotal"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next, jumper"
            background
            @current-change="loadResources"
            @size-change="handleResSizeChange"
          />
        </div>
      </el-tab-pane>

      <el-tab-pane label="清理日志" name="cleanup">
        <div class="filter-bar sketch-card">
          <div class="filter-left">
            <el-input
              v-model="cleanQuery.keyword"
              placeholder="搜索任务ID"
              :prefix-icon="Search"
              clearable
              class="search-input"
              @keyup.enter="loadCleanupLogs"
              @clear="loadCleanupLogs"
            />
            <el-button :icon="Refresh" @click="loadCleanupLogs">刷新</el-button>
          </div>
        </div>

        <div class="table-card sketch-card" v-loading="cleanLoading">
          <el-table :data="cleanList" style="width: 100%" row-key="id" stripe>
            <el-table-column label="资源类型" width="120" align="center" prop="resourceType" />
            <el-table-column label="清理条数" width="100" align="right" prop="cleanedCount" />
            <el-table-column label="释放空间(KB)" width="130" align="right">
              <template #default="{ row }">{{ formatNumber(row.freedSizeKb ?? row.freedSize ?? 0) }}</template>
            </el-table-column>
            <el-table-column label="任务ID" width="110" align="center" prop="taskId" />
            <el-table-column label="触发方式" width="110" align="center" prop="triggerType" />
            <el-table-column label="状态" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : row.status === 0 ? 'warning' : 'danger'" effect="plain" round size="small">
                  {{ row.status === 1 ? '成功' : row.status === 0 ? '进行中' : '失败' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="清理时间" width="160">
              <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
            </el-table-column>
            <el-table-column label="备注" min-width="160" prop="remark" show-overflow-tooltip />
          </el-table>

          <div class="pagination">
            <el-pagination
              v-model:current-page="cleanQuery.page"
              v-model:page-size="cleanQuery.size"
              :total="cleanTotal"
              :page-sizes="[20, 50, 100]"
              layout="total, sizes, prev, pager, next, jumper"
              background
              @current-change="loadCleanupLogs"
              @size-change="handleCleanSizeChange"
            />
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog
      v-model="signUrlDialogVisible"
      title="签名下载URL"
      width="600px"
    >
      <div class="sign-url-block">
        <el-input
          v-model="currentSignUrl"
          type="textarea"
          :rows="4"
          readonly
          placeholder="加载中..."
        />
      </div>
      <div class="sign-actions">
        <el-button type="primary" :icon="DocumentCopy" :disabled="!currentSignUrl" @click="copySignUrl">
          复制URL
        </el-button>
        <el-button v-if="currentSignUrl" type="success" :icon="Link" @click="openSignUrlInNewTab">
          在新标签页打开
        </el-button>
      </div>
      <template #footer>
        <button class="sketch-btn" @click="signUrlDialogVisible = false">关闭</button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, DocumentCopy, Link, Refresh, Document } from '@element-plus/icons-vue'
import request from '@/api/request'

const activeSubTab = ref('files')
const viewMode = ref<'table' | 'card'>('table')
const hoverCard = ref<number | null>(null)

function fmtTime(t?: string) {
  if (!t) return '-'
  return t.replace('T', ' ').slice(0, 16)
}

function formatSize(bytes?: number) {
  if (!bytes) return '0 B'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(2)} MB`
  return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`
}

function formatNumber(n: number) {
  return n.toLocaleString('zh-CN')
}

const fileTypeLabelMap: Record<number, string> = { 1: '图片', 2: '音频', 3: '视频', 4: '文档', 5: '其他' }
const fileTypeTagMap: Record<number, 'primary' | 'success' | 'warning' | 'info' | 'danger'> = {
  1: 'success', 2: 'warning', 3: 'primary', 4: 'info', 5: 'danger'
}

function fileTypeLabel(t?: number | string) {
  const n = typeof t === 'string' ? parseInt(t, 10) : t
  return n != null ? (fileTypeLabelMap[n] || String(n)) : '-'
}
function fileTypeTag(t?: number | string) {
  const n = typeof t === 'string' ? parseInt(t, 10) : t
  return n != null ? (fileTypeTagMap[n] || 'info') : 'info' as const
}
function isImage(t?: number | string) { return t === 1 }
function isVideo(t?: number | string) { return t === 3 }
function onImgError(e: Event) { (e.target as HTMLElement).style.display = 'none' }

// ===== 资源文件 =====
const resLoading = ref(false)
const resList = ref<any[]>([])
const resTotal = ref(0)
const resQuery = reactive({
  page: 1, size: 12,
  file_name: '' as string | undefined,
  file_type: undefined as string | undefined,
  task_id: undefined as string | undefined
})

async function loadResources() {
  resLoading.value = true
  try {
    const params: any = { page: resQuery.page, size: resQuery.size }
    if (resQuery.file_name) params.file_name = resQuery.file_name
    if (resQuery.file_type) params.file_type = Number(resQuery.file_type)
    if (resQuery.task_id) params.task_id = resQuery.task_id
    const res: any = await request.get('/api/resource/page', { params })
    resList.value = res.records || res || []
    resTotal.value = res.total || resList.value.length
  } catch (e) { /* 拦截器已提示 */ }
  finally { resLoading.value = false }
}
function handleResSizeChange() { resQuery.page = 1; loadResources() }

async function handleDeleteResource(row: any) {
  try {
    await ElMessageBox.confirm(
      `确定删除资源「${row.fileName || row.id}」吗？此操作不可恢复。`,
      '删除资源',
      { type: 'error', confirmButtonText: '删除', confirmButtonClass: 'el-button--danger' }
    )
    await request.delete(`/api/resource/${row.id}`)
    ElMessage.success('资源已删除')
    loadResources()
  } catch (_) { /* 用户取消 */ }
}

const signUrlDialogVisible = ref(false)
const currentSignUrl = ref('')

async function openSignUrl(row: any) {
  currentSignUrl.value = ''
  signUrlDialogVisible.value = true
  try {
    const res: any = await request.get(`/api/resource/${row.id}/sign-url`)
    currentSignUrl.value = typeof res === 'string' ? res : (res?.signUrl || res?.url || res?.data || '')
    if (!currentSignUrl.value) ElMessage.warning('未获取到签名URL')
  } catch (e: any) {
    ElMessage.error(e?.message || '获取签名URL失败')
  }
}

async function copySignUrl() {
  if (!currentSignUrl.value) return
  try {
    await navigator.clipboard.writeText(currentSignUrl.value)
    ElMessage.success('已复制到剪贴板')
  } catch (_) {
    const ta = document.createElement('textarea')
    ta.value = currentSignUrl.value
    document.body.appendChild(ta)
    ta.select()
    try { document.execCommand('copy'); ElMessage.success('已复制') } catch { ElMessage.error('复制失败') }
    document.body.removeChild(ta)
  }
}

function openSignUrlInNewTab() {
  if (currentSignUrl.value) window.open(currentSignUrl.value, '_blank')
}

// ===== 清理日志 =====
const cleanLoading = ref(false)
const cleanList = ref<any[]>([])
const cleanTotal = ref(0)
const cleanQuery = reactive({ page: 1, size: 20, keyword: '' })

async function loadCleanupLogs() {
  cleanLoading.value = true
  try {
    const params: any = { page: cleanQuery.page, size: cleanQuery.size }
    if (cleanQuery.keyword) params.keyword = cleanQuery.keyword
    const res: any = await request.get('/api/resource/cleanup-log/page', { params })
    cleanList.value = res.records || res || []
    cleanTotal.value = res.total || cleanList.value.length
  } catch (e) { /* 拦截器已提示 */ }
  finally { cleanLoading.value = false }
}
function handleCleanSizeChange() { cleanQuery.page = 1; loadCleanupLogs() }

onMounted(() => { loadResources() })
</script>

<style scoped>
.resource-center {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.sub-tabs :deep(.el-tabs__header) {
  margin-bottom: 16px;
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

.search-input {
  width: 220px;
}

.status-select {
  width: 110px;
}

.id-input {
  width: 130px;
}

/* ===== 列表模式 ===== */
.table-card {
  padding: 16px 18px 18px;
}

.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.action-btns {
  display: inline-flex;
  gap: 4px;
}

.task-id-tag {
  font-family: monospace;
  font-size: 12px;
  background: var(--cd-bg-soft);
  color: var(--cd-primary-hover);
  padding: 2px 6px;
  border-radius: 4px;
  border: 1px solid var(--cd-border);
}

.text-muted {
  color: var(--cd-text-secondary);
}

/* ===== 卡片模式 ===== */
.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 16px;
  padding: 4px;
}

.resource-card {
  border-radius: 10px;
  overflow: hidden;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
  cursor: default;
}

.resource-card:hover {
  transform: translateY(-3px);
  box-shadow: 3px 6px 0 0 var(--cd-shadow);
}

.card-preview {
  position: relative;
  width: 100%;
  height: 150px;
  background: var(--cd-bg-soft);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border-radius: 10px 10px 0 0;
}

.preview-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.preview-icon {
  color: var(--cd-text-secondary);
}

.card-overlay {
  position: absolute;
  inset: 0;
  background: color-mix(in srgb, var(--cd-bg) 75%, transparent);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  opacity: 0;
  transition: opacity 0.2s ease;
}

.resource-card:hover .card-overlay {
  opacity: 1;
}

.card-body {
  padding: 10px 12px 12px;
}

.card-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--cd-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-bottom: 6px;
}

.card-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
}

.card-size {
  font-size: 12px;
  color: var(--cd-text-secondary);
  margin-left: auto;
}

.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 11px;
  color: var(--cd-text-secondary);
}

.card-task {
  font-family: monospace;
  background: var(--cd-bg-soft);
  color: var(--cd-primary-hover);
  padding: 1px 6px;
  border-radius: 3px;
  border: 1px solid var(--cd-border);
}

.empty-state {
  grid-column: 1 / -1;
  padding: 60px 0;
}

/* ===== 签名 URL 弹窗 ===== */
.sign-url-block {
  margin-bottom: 12px;
}

.sign-actions {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
}
</style>
