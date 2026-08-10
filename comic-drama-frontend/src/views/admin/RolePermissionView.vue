<template>
  <div class="role-permission">
    <el-tabs v-model="activeSubTab" class="sub-tabs">
      <el-tab-pane label="角色管理" name="roles">
        <div class="filter-bar sketch-card">
          <div class="filter-left">
            <el-input
              v-model="roleQuery.keyword"
              placeholder="搜索角色名 / 编码"
              :prefix-icon="Search"
              clearable
              class="search-input"
              @keyup.enter="loadRoles"
              @clear="loadRoles"
            />
          </div>
          <div class="filter-right">
            <button class="sketch-btn" @click="openCreateRole">
              <el-icon><Plus /></el-icon>
              新建角色
            </button>
          </div>
        </div>

        <div class="table-card sketch-card" v-loading="roleLoading">
          <el-table :data="roleList" style="width: 100%" row-key="id" stripe>
            <el-table-column label="角色编码" min-width="140" prop="roleCode" />
            <el-table-column label="角色名称" min-width="140" prop="roleName" />
            <el-table-column label="描述" min-width="200" prop="description" />
            <el-table-column label="状态" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'info'" effect="plain" round size="small">
                  {{ row.status === 1 ? '启用' : '禁用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="创建时间" width="160">
              <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="260" fixed="right" align="center">
              <template #default="{ row }">
                <div class="action-btns">
                  <el-button link type="warning" size="small" @click="openAssignPermission(row)">分配权限</el-button>
                  <el-button link type="primary" size="small" @click="openEditRole(row)">编辑</el-button>
                  <el-button link type="danger" size="small" @click="handleDeleteRole(row)">删除</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>

          <div class="pagination">
            <el-pagination
              v-model:current-page="roleQuery.page"
              v-model:page-size="roleQuery.size"
              :total="roleTotal"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next, jumper"
              background
              @current-change="loadRoles"
              @size-change="handleRoleSizeChange"
            />
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="权限管理" name="permissions">
        <div class="filter-bar sketch-card">
          <div class="filter-left">
            <el-input
              v-model="permQuery.keyword"
              placeholder="搜索权限名 / 编码"
              :prefix-icon="Search"
              clearable
              class="search-input"
              @keyup.enter="loadPermissions"
              @clear="loadPermissions"
            />
            <el-select
              v-model="permQuery.permType"
              placeholder="全部类型"
              clearable
              class="status-select"
              @change="loadPermissions"
            >
              <el-option label="菜单" value="menu" />
              <el-option label="按钮" value="button" />
              <el-option label="接口" value="api" />
            </el-select>
          </div>
          <div class="filter-right">
            <button class="sketch-btn" @click="openCreatePermission">
              <el-icon><Plus /></el-icon>
              新建权限
            </button>
          </div>
        </div>

        <div class="table-card sketch-card" v-loading="permLoading">
          <el-table :data="permList" style="width: 100%" row-key="id" stripe>
            <el-table-column label="权限名" min-width="140" prop="permissionName" />
            <el-table-column label="类型" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="permTypeTag(row.permType)" effect="light" round size="small">
                  {{ permTypeText(row.permType) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="编码" min-width="160" prop="permissionCode" />
            <el-table-column label="路由/路径" min-width="180" prop="pathOrRoute" />
            <el-table-column label="排序" width="80" align="right" prop="sortOrder" />
            <el-table-column label="父级ID" width="90" align="center" prop="parentId" />
            <el-table-column label="操作" width="160" fixed="right" align="center">
              <template #default="{ row }">
                <div class="action-btns">
                  <el-button link type="primary" size="small" @click="openEditPermission(row)">编辑</el-button>
                  <el-button link type="danger" size="small" @click="handleDeletePermission(row)">删除</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>

          <div class="pagination">
            <el-pagination
              v-model:current-page="permQuery.page"
              v-model:page-size="permQuery.size"
              :total="permTotal"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next, jumper"
              background
              @current-change="loadPermissions"
              @size-change="handlePermSizeChange"
            />
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog
      v-model="roleDialogVisible"
      :title="editingRole ? '编辑角色' : '新建角色'"
      width="480px"
      :close-on-click-modal="false"
    >
      <el-form ref="roleFormRef" :model="roleForm" :rules="roleFormRules" label-width="90px">
        <el-form-item label="角色编码" prop="roleCode">
          <el-input v-model="roleForm.roleCode" placeholder="如 ADMIN / USER" />
        </el-form-item>
        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="roleForm.roleName" placeholder="显示名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="roleForm.description" type="textarea" :rows="2" placeholder="选填" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="roleForm.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <button class="sketch-btn sketch-btn--ghost" @click="roleDialogVisible = false">取消</button>
        <button class="sketch-btn" :disabled="roleSubmitting" @click="handleSubmitRole">
          {{ roleSubmitting ? '提交中...' : '确认' }}
        </button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="permDialogVisible"
      :title="editingPermission ? '编辑权限' : '新建权限'"
      width="520px"
      :close-on-click-modal="false"
    >
      <el-form ref="permFormRef" :model="permForm" :rules="permFormRules" label-width="100px">
        <el-form-item label="权限名" prop="permissionName">
          <el-input v-model="permForm.permissionName" placeholder="如用户管理" />
        </el-form-item>
        <el-form-item label="类型" prop="permType">
          <el-select v-model="permForm.permType" placeholder="选择类型" style="width: 100%">
            <el-option label="菜单 menu" value="menu" />
            <el-option label="按钮 button" value="button" />
            <el-option label="接口 api" value="api" />
          </el-select>
        </el-form-item>
        <el-form-item label="编码" prop="permissionCode">
          <el-input v-model="permForm.permissionCode" placeholder="如 sys:user:list" />
        </el-form-item>
        <el-form-item label="路由/路径">
          <el-input v-model="permForm.pathOrRoute" placeholder="菜单路由或接口路径" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="permForm.sortOrder" :min="0" :step="1" />
        </el-form-item>
        <el-form-item label="父级ID">
          <el-input-number v-model="permForm.parentId" :min="0" :step="1" />
        </el-form-item>
      </el-form>
      <template #footer>
        <button class="sketch-btn sketch-btn--ghost" @click="permDialogVisible = false">取消</button>
        <button class="sketch-btn" :disabled="permSubmitting" @click="handleSubmitPermission">
          {{ permSubmitting ? '提交中...' : '确认' }}
        </button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="assignDialogVisible"
      :title="`分配权限 - ${assignRole?.roleName || ''}`"
      width="560px"
      :close-on-click-modal="false"
    >
      <div v-loading="assignLoading" class="assign-tree-wrap">
        <el-tree
          ref="permTreeRef"
          v-loading="permTreeLoading"
          :data="permTreeData"
          show-checkbox
          node-key="id"
          :default-checked-keys="assignedPermIds"
          :props="{ label: 'permissionName', children: 'children' }"
        />
      </div>
      <template #footer>
        <button class="sketch-btn sketch-btn--ghost" @click="assignDialogVisible = false">取消</button>
        <button class="sketch-btn" :disabled="assignSubmitting" @click="handleSubmitAssign">
          {{ assignSubmitting ? '保存中...' : '确认保存' }}
        </button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Search, Plus } from '@element-plus/icons-vue'
import {
  listRoles,
  createRole,
  updateRole,
  deleteRole,
  listPermissions,
  createPermission,
  updatePermission,
  deletePermission,
  listRolePermissions,
  updateRolePermissions
} from '@/api/admin'

const activeSubTab = ref('roles')

function fmtTime(t?: string) {
  if (!t) return '-'
  return t.replace('T', ' ').slice(0, 16)
}

const permTypeMap: Record<string, string> = { menu: '菜单', button: '按钮', api: '接口' }
const permTypeTagMap: Record<string, 'primary' | 'success' | 'warning'> = {
  menu: 'primary', button: 'success', api: 'warning'
}
function permTypeText(t?: string) { return t ? permTypeMap[t] || '-' : '-' }
function permTypeTag(t?: string) { return t ? permTypeTagMap[t] || 'info' : 'info' as const }

// ===== 角色管理 =====
const roleLoading = ref(false)
const roleList = ref<any[]>([])
const roleTotal = ref(0)
const roleQuery = reactive({ page: 1, size: 10, keyword: '' })

async function loadRoles() {
  roleLoading.value = true
  try {
    const res: any = await listRoles(roleQuery)
    roleList.value = res.records || res || []
    roleTotal.value = res.total || roleList.value.length
  } catch (e) { /* 拦截器已提示 */ }
  finally { roleLoading.value = false }
}
function handleRoleSizeChange() { roleQuery.page = 1; loadRoles() }

const roleDialogVisible = ref(false)
const roleSubmitting = ref(false)
const editingRole = ref<any>(null)
const roleFormRef = ref<FormInstance>()
const roleForm = reactive({ roleCode: '', roleName: '', description: '', status: 1 })
const roleFormRules: FormRules = {
  roleCode: [{ required: true, message: '请输入角色编码', trigger: 'blur' }],
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }]
}

function openCreateRole() {
  editingRole.value = null
  Object.assign(roleForm, { roleCode: '', roleName: '', description: '', status: 1 })
  roleDialogVisible.value = true
}
function openEditRole(row: any) {
  editingRole.value = row
  Object.assign(roleForm, {
    roleCode: row.roleCode, roleName: row.roleName,
    description: row.description || '', status: row.status ?? 1
  })
  roleDialogVisible.value = true
}
async function handleSubmitRole() {
  if (!roleFormRef.value) return
  await roleFormRef.value.validate(async (valid) => {
    if (!valid) return
    roleSubmitting.value = true
    try {
      if (editingRole.value) {
        await updateRole({ id: editingRole.value.id, ...roleForm })
        ElMessage.success('角色已更新')
      } else {
        await createRole(roleForm)
        ElMessage.success('角色已创建')
      }
      roleDialogVisible.value = false
      loadRoles()
    } catch (e: any) { ElMessage.error(e?.message || '保存失败') }
    finally { roleSubmitting.value = false }
  })
}
async function handleDeleteRole(row: any) {
  try {
    await ElMessageBox.confirm(`确定删除角色「${row.roleName}」吗？`, '删除角色', { type: 'error' })
    await deleteRole(row.id)
    ElMessage.success('角色已删除')
    loadRoles()
  } catch (_) { /* 用户取消 */ }
}

// ===== 权限管理 =====
const permLoading = ref(false)
const permList = ref<any[]>([])
const permTotal = ref(0)
const permQuery = reactive({ page: 1, size: 10, keyword: '', permType: undefined as string | undefined })

async function loadPermissions() {
  permLoading.value = true
  try {
    const res: any = await listPermissions(permQuery)
    permList.value = res.records || res || []
    permTotal.value = res.total || permList.value.length
  } catch (e) { /* 拦截器已提示 */ }
  finally { permLoading.value = false }
}
function handlePermSizeChange() { permQuery.page = 1; loadPermissions() }

const permDialogVisible = ref(false)
const permSubmitting = ref(false)
const editingPermission = ref<any>(null)
const permFormRef = ref<FormInstance>()
const permForm = reactive({
  permissionName: '', permType: 'menu', permissionCode: '',
  pathOrRoute: '', sortOrder: 0, parentId: 0
})
const permFormRules: FormRules = {
  permissionName: [{ required: true, message: '请输入权限名', trigger: 'blur' }],
  permType: [{ required: true, message: '请选择类型', trigger: 'change' }],
  permissionCode: [{ required: true, message: '请输入编码', trigger: 'blur' }]
}

function openCreatePermission() {
  editingPermission.value = null
  Object.assign(permForm, {
    permissionName: '', permType: 'menu', permissionCode: '',
    pathOrRoute: '', sortOrder: 0, parentId: 0
  })
  permDialogVisible.value = true
}
function openEditPermission(row: any) {
  editingPermission.value = row
  Object.assign(permForm, {
    permissionName: row.permissionName, permType: row.permType || 'menu',
    permissionCode: row.permissionCode, pathOrRoute: row.pathOrRoute || row.path || row.route || '',
    sortOrder: row.sortOrder ?? 0, parentId: row.parentId ?? 0
  })
  permDialogVisible.value = true
}
async function handleSubmitPermission() {
  if (!permFormRef.value) return
  await permFormRef.value.validate(async (valid) => {
    if (!valid) return
    permSubmitting.value = true
    try {
      if (editingPermission.value) {
        await updatePermission({ id: editingPermission.value.id, ...permForm })
        ElMessage.success('权限已更新')
      } else {
        await createPermission(permForm)
        ElMessage.success('权限已创建')
      }
      permDialogVisible.value = false
      loadPermissions()
    } catch (e: any) { ElMessage.error(e?.message || '保存失败') }
    finally { permSubmitting.value = false }
  })
}
async function handleDeletePermission(row: any) {
  try {
    await ElMessageBox.confirm(`确定删除权限「${row.permissionName}」吗？`, '删除权限', { type: 'error' })
    await deletePermission(row.id)
    ElMessage.success('权限已删除')
    loadPermissions()
  } catch (_) { /* 用户取消 */ }
}

// ===== 分配权限 =====
const assignDialogVisible = ref(false)
const assignLoading = ref(false)
const assignSubmitting = ref(false)
const assignRole = ref<any>(null)
const assignedPermIds = ref<number[]>([])
const permTreeRef = ref<any>()
const permTreeLoading = ref(false)
const permTreeData = ref<any[]>([])

function buildPermTree(list: any[]): any[] {
  const map: Record<number, any> = {}
  const roots: any[] = []
  for (const item of list) {
    map[item.id] = { ...item, children: [] }
  }
  for (const item of list) {
    const node = map[item.id]
    const pid = item.parentId || 0
    if (pid && map[pid]) {
      map[pid].children.push(node)
    } else {
      roots.push(node)
    }
  }
  const sortFn = (a: any, b: any) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0)
  const walk = (nodes: any[]) => {
    nodes.sort(sortFn)
    nodes.forEach(n => { if (n.children?.length) walk(n.children) })
  }
  walk(roots)
  return roots
}

async function openAssignPermission(row: any) {
  assignRole.value = row
  assignDialogVisible.value = true
  assignLoading.value = true
  permTreeLoading.value = true
  assignedPermIds.value = []
  permTreeData.value = []
  try {
    const [permRes, assignRes]: any[] = await Promise.all([
      listPermissions({ page: 1, size: 9999 }),
      listRolePermissions(row.id)
    ])
    const allPerms = permRes.records || permRes || []
    permTreeData.value = buildPermTree(allPerms)
    assignedPermIds.value = Array.isArray(assignRes) ? assignRes : (assignRes?.permissionIds || assignRes?.records || [])
  } catch (e) { /* 拦截器已提示 */ }
  finally {
    assignLoading.value = false
    permTreeLoading.value = false
  }
}

async function handleSubmitAssign() {
  if (!assignRole.value || !permTreeRef.value) return
  assignSubmitting.value = true
  try {
    const checked = permTreeRef.value.getCheckedKeys(false) as number[]
    const half = permTreeRef.value.getHalfCheckedKeys() as number[]
    const allIds = [...checked, ...half]
    await updateRolePermissions(assignRole.value.id, allIds)
    ElMessage.success('权限分配已保存')
    assignDialogVisible.value = false
  } catch (e: any) { ElMessage.error(e?.message || '保存失败') }
  finally { assignSubmitting.value = false }
}

onMounted(() => {
  loadRoles()
})
</script>

<style scoped>
.role-permission {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.sub-tabs :deep(.el-tabs__header) {
  margin-bottom: 16px;
}

.filter-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 18px;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}

.filter-left,
.filter-right {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.search-input {
  width: 240px;
}

.status-select {
  width: 140px;
}

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

.assign-tree-wrap {
  min-height: 320px;
  max-height: 480px;
  overflow: auto;
  padding: 8px;
  border: 1px solid var(--cd-border);
  border-radius: 6px;
}
</style>
