<template>
  <div class="login-page">
    <!-- 装饰：笔记本手账风格SVG插画 -->
    <div class="illustration illustration--notebook">
      <SpiralNotebook :size="260" :rotate="-8" />
    </div>
    <div class="illustration illustration--pencil">
      <Pencil :size="200" :rotate="-35" variant="primary" />
    </div>
    <div class="illustration illustration--sticky">
      <StickyNote :size="140" :rotate="12" color="accent" />
    </div>
    <div class="illustration illustration--marker">
      <Marker :size="160" :rotate="25" color="warn" />
    </div>
    <div class="illustration illustration--doodle">
      <Doodles :size="100" :rotate="-15" type="heart" />
    </div>

    <div class="login-card sketch-card">
      <div class="login-header">
        <img class="logo-img" :src="logoUrl" alt="漫剧AI" />
        <h1 class="title">漫剧AI生成Agent</h1>
        <p class="subtitle">从一句话到一部漫剧 · 9步AI流水线</p>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        size="large"
        @submit.prevent="handleLogin"
      >
        <el-form-item label="账号" prop="username">
          <el-input
            v-model="form.username"
            placeholder="请输入用户名"
            :prefix-icon="User"
            clearable
          />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            :prefix-icon="Lock"
            show-password
            @keyup.enter="handleLogin"
          />
        </el-form-item>

        <div class="form-extra">
          <el-checkbox v-model="remember">记住账号</el-checkbox>
          <a class="forgot" @click="ElMessage.info('暂未开放找回密码')">忘记密码？</a>
        </div>

        <button type="button" class="sketch-btn login-btn" :disabled="loading" @click="handleLogin">
          <el-icon v-if="loading" class="is-loading"><Loading /></el-icon>
          <span>{{ loading ? '登录中…' : '登 录' }}</span>
        </button>
      </el-form>

      <div class="register-tip">
        还没有账号？
        <a class="link" @click="openRegister">立即注册</a>
      </div>

      <div class="hint">
        <el-icon><InfoFilled /></el-icon>
        演示账号：admin / 123456
      </div>
    </div>

    <!-- 注册弹窗 -->
    <el-dialog v-model="registerVisible" title="注册新账号" width="420px" align-center>
      <el-form
        ref="regFormRef"
        :model="regForm"
        :rules="regRules"
        label-position="top"
        size="large"
      >
        <el-form-item label="用户名" prop="username">
          <el-input v-model="regForm.username" placeholder="3-20 位字符" :prefix-icon="User" />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="regForm.nickname" placeholder="选填" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="regForm.password"
            type="password"
            placeholder="6-20 位"
            :prefix-icon="Lock"
            show-password
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <button class="sketch-btn sketch-btn--ghost" @click="registerVisible = false">取消</button>
        <button class="sketch-btn" :disabled="regLoading" @click="handleRegister">
          {{ regLoading ? '提交中…' : '注 册' }}
        </button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { User, Lock, Loading, InfoFilled } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { useThemeStore } from '@/stores/theme'
import { register as registerApi } from '@/api/auth'
import { SpiralNotebook, Pencil, StickyNote, Marker, Doodles } from '@/components/illustrations'
import logoSoft from '@/assets/ComicDramaLogo-soft.png'
import logoBright from '@/assets/ComicDramaLogo-bright.png'
import logoDark from '@/assets/ComicDramaLogo-dark.png'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const themeStore = useThemeStore()

const logoMap = { soft: logoSoft, bright: logoBright, dark: logoDark }
const logoUrl = computed(() => logoMap[themeStore.current])

const formRef = ref<FormInstance>()
const form = reactive({
  username: localStorage.getItem('cd_remember_user') || 'admin',
  password: localStorage.getItem('cd_remember_user') ? '' : '123456'
})
const remember = ref(!!localStorage.getItem('cd_remember_user'))
const loading = ref(false)

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少 6 位', trigger: 'blur' }
  ]
}

async function handleLogin() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      await userStore.login({ username: form.username, password: form.password })
      if (remember.value) {
        localStorage.setItem('cd_remember_user', form.username)
      } else {
        localStorage.removeItem('cd_remember_user')
      }
      ElMessage.success(`欢迎回来，${userStore.nickname || form.username}`)
      const redirect = (route.query.redirect as string) || '/'
      router.replace(redirect)
    } catch (e) {
      // 拦截器已统一提示
    } finally {
      loading.value = false
    }
  })
}

// ====== 注册 ======
const registerVisible = ref(false)
const regLoading = ref(false)
const regFormRef = ref<FormInstance>()
const regForm = reactive({ username: '', nickname: '', password: '' })
const regRules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '3-20 位字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '6-20 位', trigger: 'blur' }
  ]
}

function openRegister() {
  registerVisible.value = true
  regForm.username = ''
  regForm.nickname = ''
  regForm.password = ''
}

async function handleRegister() {
  if (!regFormRef.value) return
  await regFormRef.value.validate(async (valid) => {
    if (!valid) return
    regLoading.value = true
    try {
      await registerApi({
        username: regForm.username,
        password: regForm.password,
        nickname: regForm.nickname
      })
      ElMessage.success('注册成功，已为你自动填充账号')
      form.username = regForm.username
      form.password = regForm.password
      registerVisible.value = false
    } catch (e) {
      /* 拦截器提示 */
    } finally {
      regLoading.value = false
    }
  })
}
</script>

<style scoped>
.login-page {
  position: relative;
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  overflow: hidden;
}

.login-card {
  width: 100%;
  max-width: 420px;
  padding: 40px 36px 28px;
  position: relative;
  z-index: 2;
}

.login-header {
  text-align: center;
  margin-bottom: 28px;
}

.logo-img {
  width: 120px;
  height: auto;
  margin: 0 auto 14px;
  display: block;
}

.title {
  margin: 0;
  font-size: 24px;
  font-weight: 800;
  color: var(--cd-text);
  letter-spacing: 1px;
}

.subtitle {
  margin: 8px 0 0;
  font-size: 13px;
  color: var(--cd-text-secondary);
}

.form-extra {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 4px 0 20px;
  font-size: 13px;
}

.forgot,
.link {
  color: var(--cd-primary);
  cursor: pointer;
}
.forgot:hover,
.link:hover {
  color: var(--cd-primary-hover);
}

.login-btn {
  width: 100%;
  height: 44px;
  font-size: 16px;
  letter-spacing: 4px;
}

.register-tip {
  margin-top: 22px;
  text-align: center;
  font-size: 13px;
  color: var(--cd-text-secondary);
}

.hint {
  margin-top: 18px;
  padding: 8px 12px;
  border-radius: 6px;
  background-color: var(--cd-bg-soft);
  font-size: 12px;
  color: var(--cd-text-secondary);
  display: flex;
  align-items: center;
  gap: 6px;
  justify-content: center;
}

/* 装饰插画容器 */
.illustration {
  position: absolute;
  z-index: 1;
  pointer-events: none;
  user-select: none;
  opacity: 0.9;
  transition: opacity 0.3s ease;
}
.illustration--notebook {
  top: 8%;
  left: 4%;
  opacity: 0.5;
}
.illustration--pencil {
  bottom: 14%;
  left: 6%;
  opacity: 0.6;
}
.illustration--sticky {
  top: 6%;
  right: 6%;
  opacity: 0.65;
}
.illustration--marker {
  bottom: 10%;
  right: 8%;
  opacity: 0.55;
}
.illustration--doodle {
  top: 30%;
  right: 18%;
  opacity: 0.7;
}

@media (max-width: 480px) {
  .illustration {
    opacity: 0.25;
  }
  .login-card {
    padding: 32px 22px 22px;
  }
}
</style>
