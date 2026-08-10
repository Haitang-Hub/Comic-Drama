<template>
  <div class="profile" v-loading="loading">
    <!-- 左侧：用户信息卡片 -->
    <div class="profile-left">
      <div class="user-card sketch-card">
        <div class="avatar-section">
          <el-avatar :size="88" class="user-avatar-large">
            <img v-if="profile.avatar" :src="profile.avatar" alt="avatar" />
            <span v-else>{{ avatarText }}</span>
          </el-avatar>
          <div class="avatar-upload" @click="triggerAvatarUpload">
            <el-icon><Camera /></el-icon>
            更换头像
          </div>
          <input
            ref="avatarInputRef"
            type="file"
            accept="image/*"
            style="display: none"
            @change="handleAvatarChange"
          />
        </div>
        <div class="user-info">
          <h2 class="user-nickname">{{ profile.nickname || profile.username }}</h2>
          <p class="user-username">@{{ profile.username }}</p>
          <div class="user-roles" v-if="profile.roleNames?.length">
            <el-tag v-for="r in profile.roleNames" :key="r" effect="light" round size="small">
              {{ r }}
            </el-tag>
          </div>
          <div class="user-status">
            <el-tag
              :type="profile.status === 1 ? 'success' : 'info'"
              effect="plain"
              size="small"
              round
            >
              {{ profile.status === 1 ? '正常' : '禁用' }}
            </el-tag>
            <span class="user-join-time">加入于 {{ fmtDate(profile.createTime) }}</span>
          </div>
        </div>
        <div class="user-decor">
          <StickyNote :size="70" :rotate="8" color="soft" />
        </div>
      </div>

      <!-- 操作统计 -->
      <div class="stats-card sketch-card">
        <div class="stats-head">
          <h3>创作统计</h3>
          <Doodles :size="40" :rotate="-5" type="stars" :opacity="0.7" />
        </div>
        <div class="stats-grid">
          <div class="stat-item">
            <div class="stat-value">{{ stats.taskCount }}</div>
            <div class="stat-label">总任务</div>
          </div>
          <div class="stat-item stat-success">
            <div class="stat-value">{{ stats.taskDone }}</div>
            <div class="stat-label">已完成</div>
          </div>
          <div class="stat-item stat-danger">
            <div class="stat-value">{{ stats.taskFailed }}</div>
            <div class="stat-label">失败</div>
          </div>
          <div class="stat-item stat-primary">
            <div class="stat-value">{{ stats.workCount }}</div>
            <div class="stat-label">作品数</div>
          </div>
        </div>
      </div>
    </div>

    <!-- 右侧：Tab 内容 -->
    <div class="profile-right">
      <el-tabs v-model="activeTab" class="profile-tabs">
        <!-- 基本信息 -->
        <el-tab-pane label="基本信息" name="info">
          <div class="tab-panel sketch-card">
            <div class="panel-head">
              <h3>基本信息</h3>
              <span class="panel-tip">更新您的个人资料和联系方式</span>
            </div>
            <el-form
              ref="infoFormRef"
              :model="infoForm"
              :rules="infoRules"
              label-width="90px"
              class="info-form"
            >
              <el-form-item label="昵称" prop="nickname">
                <el-input v-model="infoForm.nickname" placeholder="请输入昵称" maxlength="20" />
              </el-form-item>
              <el-form-item label="邮箱" prop="email">
                <el-input v-model="infoForm.email" placeholder="请输入邮箱" />
              </el-form-item>
              <el-form-item label="手机号" prop="phone">
                <el-input v-model="infoForm.phone" placeholder="请输入手机号" />
              </el-form-item>
              <el-form-item label="性别">
                <el-radio-group v-model="infoForm.gender">
                  <el-radio :model-value="1">男</el-radio>
                  <el-radio :model-value="2">女</el-radio>
                  <el-radio :model-value="0">保密</el-radio>
                </el-radio-group>
              </el-form-item>
              <el-form-item label="用户名">
                <el-input :model-value="profile.username" disabled />
              </el-form-item>
              <el-form-item>
                <button class="sketch-btn" :disabled="submitting" @click="handleUpdateInfo">
                  <el-icon v-if="submitting"><Loading /></el-icon>
                  {{ submitting ? '保存中...' : '保存修改' }}
                </button>
              </el-form-item>
            </el-form>
          </div>
        </el-tab-pane>

        <!-- 修改密码 -->
        <el-tab-pane label="修改密码" name="password">
          <div class="tab-panel sketch-card">
            <div class="panel-head">
              <h3>修改密码</h3>
              <span class="panel-tip">定期更换密码有助于账户安全</span>
            </div>
            <el-form
              ref="pwdFormRef"
              :model="pwdForm"
              :rules="pwdRules"
              label-width="110px"
              class="pwd-form"
            >
              <el-form-item label="当前密码" prop="oldPassword">
                <el-input v-model="pwdForm.oldPassword" type="password" show-password placeholder="请输入当前密码" />
              </el-form-item>
              <el-form-item label="新密码" prop="newPassword">
                <el-input
                  v-model="pwdForm.newPassword"
                  type="password"
                  show-password
                  placeholder="至少 8 位，包含字母和数字"
                />
              </el-form-item>
              <el-form-item label="确认密码" prop="confirmPassword">
                <el-input
                  v-model="pwdForm.confirmPassword"
                  type="password"
                  show-password
                  placeholder="再次输入新密码"
                />
              </el-form-item>
              <el-form-item>
                <button class="sketch-btn" :disabled="submitting" @click="handleUpdatePwd">
                  <el-icon v-if="submitting"><Loading /></el-icon>
                  {{ submitting ? '提交中...' : '确认修改' }}
                </button>
              </el-form-item>
            </el-form>
          </div>
        </el-tab-pane>

        <!-- 账户安全 -->
        <el-tab-pane label="账户安全" name="security">
          <div class="tab-panel sketch-card">
            <div class="panel-head">
              <h3>账户安全</h3>
            </div>
            <div class="security-list">
              <div class="security-item">
                <div class="sec-main">
                  <div class="sec-icon" style="background-color: var(--cd-primary)">
                    <el-icon><Lock /></el-icon>
                  </div>
                  <div class="sec-info">
                    <div class="sec-title">密码强度</div>
                    <div class="sec-desc">上次修改时间：{{ fmtDate(profile.createTime) }}</div>
                  </div>
                </div>
                <el-button link type="primary" @click="activeTab = 'password'">修改</el-button>
              </div>

              <div class="security-item">
                <div class="sec-main">
                  <div class="sec-icon" style="background-color: var(--cd-success)">
                    <el-icon><Iphone /></el-icon>
                  </div>
                  <div class="sec-info">
                    <div class="sec-title">绑定手机</div>
                    <div class="sec-desc">{{ profile.phone || '未绑定' }}</div>
                  </div>
                </div>
                <el-button
                  link
                  type="primary"
                  @click="activeTab = 'info'"
                >{{ profile.phone ? '修改' : '绑定' }}</el-button>
              </div>

              <div class="security-item">
                <div class="sec-main">
                  <div class="sec-icon" style="background-color: var(--cd-warning)">
                    <el-icon><Message /></el-icon>
                  </div>
                  <div class="sec-info">
                    <div class="sec-title">绑定邮箱</div>
                    <div class="sec-desc">{{ profile.email || '未绑定' }}</div>
                  </div>
                </div>
                <el-button
                  link
                  type="primary"
                  @click="activeTab = 'info'"
                >{{ profile.email ? '修改' : '绑定' }}</el-button>
              </div>

              <div class="security-item">
                <div class="sec-main">
                  <div class="sec-icon" style="background-color: var(--cd-danger)">
                    <el-icon><SwitchButton /></el-icon>
                  </div>
                  <div class="sec-info">
                    <div class="sec-title">退出登录</div>
                    <div class="sec-desc">清除当前会话并返回登录页</div>
                  </div>
                </div>
                <el-button link type="danger" @click="handleLogout">退出</el-button>
              </div>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Loading, Camera, Lock, Iphone, Message, SwitchButton } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import {
  getProfile,
  updateProfile,
  updatePassword,
  uploadAvatar,
  getUserStats,
  type UserProfileVO,
  type UserProfileUpdateDTO,
  type PasswordUpdateDTO,
  type UserStatsVO
} from '@/api/user'
import { Doodles, StickyNote } from '@/components/illustrations'

const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const submitting = ref(false)

const profile = reactive<UserProfileVO>({
  id: 0,
  username: '',
  nickname: '',
  avatar: '',
  email: '',
  phone: '',
  gender: 0,
  status: 1,
  createTime: '',
  roleNames: []
})

const stats = reactive<UserStatsVO>({
  taskCount: 0,
  taskDone: 0,
  taskFailed: 0,
  workCount: 0,
  totalConsumeTime: 0
})

const activeTab = ref('info')
const avatarInputRef = ref<HTMLInputElement>()

const avatarText = computed(() =>
  (profile.nickname || profile.username || 'U').charAt(0).toUpperCase()
)

function fmtDate(t?: string) {
  if (!t) return '-'
  return t.replace('T', ' ').slice(0, 10)
}

// ===== 基本信息 =====
const infoFormRef = ref<FormInstance>()
const infoForm = reactive<UserProfileUpdateDTO>({
  nickname: '',
  email: '',
  phone: '',
  gender: 0
})
const infoRules: FormRules = {
  nickname: [
    { max: 20, message: '昵称不超过 20 个字符', trigger: 'blur' }
  ],
  email: [
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
  ],
  phone: [
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ]
}

async function handleUpdateInfo() {
  if (!infoFormRef.value) return
  await infoFormRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      const data: UserProfileUpdateDTO = {
        nickname: infoForm.nickname,
        email: infoForm.email,
        phone: infoForm.phone,
        gender: infoForm.gender
      }
      const res = await updateProfile(data)
      Object.assign(profile, res)
      // 同步更新 store 中的昵称
      if (res.nickname) {
        userStore.userInfo = { ...userStore.userInfo!, nickname: res.nickname }
      }
      ElMessage.success('保存成功')
    } catch (e) {
      /* 拦截器已提示 */
    } finally {
      submitting.value = false
    }
  })
}

// ===== 修改密码 =====
const pwdFormRef = ref<FormInstance>()
const pwdForm = reactive<PasswordUpdateDTO>({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})
const pwdRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 8, message: '密码至少 8 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== pwdForm.newPassword) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

async function handleUpdatePwd() {
  if (!pwdFormRef.value) return
  await pwdFormRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      await updatePassword({ ...pwdForm })
      ElMessage.success('密码修改成功，请重新登录')
      userStore.logout()
      router.replace('/login')
    } catch (e) {
      /* 拦截器已提示 */
    } finally {
      submitting.value = false
    }
  })
}

// ===== 头像上传 =====
function triggerAvatarUpload() {
  avatarInputRef.value?.click()
}

async function handleAvatarChange(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return

  if (file.size > 2 * 1024 * 1024) {
    ElMessage.warning('头像大小不能超过 2MB')
    return
  }
  if (!file.type.startsWith('image/')) {
    ElMessage.warning('请上传图片文件')
    return
  }

  try {
    submitting.value = true
    const res = await uploadAvatar(file)
    profile.avatar = res.url
    ElMessage.success('头像更换成功')
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    submitting.value = false
    // 清除 input value 以便下次选同文件也能触发 change
    if (avatarInputRef.value) avatarInputRef.value.value = ''
  }
}

// ===== 退出登录 =====
async function handleLogout() {
  await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
    type: 'warning',
    confirmButtonText: '退出',
    cancelButtonText: '取消'
  })
    .then(() => {
      userStore.logout()
      ElMessage.success('已退出登录')
      router.replace('/login')
    })
    .catch(() => {})
}

// ===== 加载数据 =====
async function loadData() {
  loading.value = true
  try {
    const [p, s] = await Promise.all([getProfile(), getUserStats()])
    Object.assign(profile, p)
    Object.assign(infoForm, {
      nickname: p.nickname || '',
      email: p.email || '',
      phone: p.phone || '',
      gender: p.gender || 0
    })
    Object.assign(stats, s)
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>
.profile {
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: 20px;
  align-items: start;
}

/* ===== 左侧卡片 ===== */
.profile-left {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.user-card {
  padding: 24px;
  text-align: center;
  position: relative;
  overflow: hidden;
}

.avatar-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
}

.user-avatar-large {
  background-color: var(--cd-primary);
  color: #fff;
  font-weight: 700;
  font-size: 32px;
  border: 3px solid var(--cd-bg-card);
  box-shadow: 0 0 0 2px var(--cd-primary);
}

.avatar-upload {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 12px;
  border: 1.5px dashed var(--cd-border);
  border-radius: 16px;
  font-size: 12px;
  color: var(--cd-text-secondary);
  cursor: pointer;
  transition: all 0.15s ease;
}
.avatar-upload:hover {
  border-color: var(--cd-primary);
  color: var(--cd-primary);
}

.user-info {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
}

.user-nickname {
  margin: 0;
  font-size: 20px;
  font-weight: 800;
  color: var(--cd-text);
}

.user-username {
  margin: 0;
  font-size: 13px;
  color: var(--cd-text-secondary);
}

.user-roles {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
  justify-content: center;
}

.user-status {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
}

.user-join-time {
  font-size: 12px;
  color: var(--cd-text-secondary);
}

.user-decor {
  position: absolute;
  top: 10px;
  right: 12px;
  opacity: 0.5;
  pointer-events: none;
}

/* ===== 统计卡片 ===== */
.stats-card {
  padding: 20px;
}

.stats-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}
.stats-head h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
  color: var(--cd-text);
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
}

.stat-item {
  padding: 12px;
  border-radius: 6px;
  border: 1.5px solid var(--cd-border);
  background-color: var(--cd-bg-soft);
  text-align: center;
}
.stat-value {
  font-size: 22px;
  font-weight: 800;
  color: var(--cd-text);
}
.stat-label {
  margin-top: 4px;
  font-size: 12px;
  color: var(--cd-text-secondary);
}
.stat-success .stat-value { color: var(--cd-success); }
.stat-danger .stat-value { color: var(--cd-danger); }
.stat-primary .stat-value { color: var(--cd-primary); }

/* ===== 右侧 Tab ===== */
.profile-right {
  min-width: 0;
}

.profile-tabs :deep(.el-tabs__item) {
  font-size: 14px;
  font-weight: 600;
}

.tab-panel {
  padding: 24px 28px;
}

.panel-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 20px;
  padding-bottom: 14px;
  border-bottom: 1.5px dashed var(--cd-border);
}
.panel-head h3 {
  margin: 0;
  font-size: 17px;
  font-weight: 800;
  color: var(--cd-text);
}
.panel-tip {
  font-size: 13px;
  color: var(--cd-text-secondary);
}

.info-form,
.pwd-form {
  max-width: 480px;
}

/* ===== 安全列表 ===== */
.security-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.security-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 18px;
  border: 1.5px solid var(--cd-border);
  border-radius: 8px;
  background-color: var(--cd-bg-soft);
}

.sec-main {
  display: flex;
  align-items: center;
  gap: 14px;
}

.sec-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 18px;
  flex-shrink: 0;
}

.sec-title {
  font-weight: 600;
  color: var(--cd-text);
  font-size: 14px;
}
.sec-desc {
  font-size: 12px;
  color: var(--cd-text-secondary);
  margin-top: 2px;
}

@media (max-width: 968px) {
  .profile {
    grid-template-columns: 1fr;
  }
}
</style>