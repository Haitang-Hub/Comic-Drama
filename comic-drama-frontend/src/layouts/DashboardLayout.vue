<template>
  <div class="layout">
    <!-- 侧边栏 -->
    <aside class="sidebar" :class="{ collapsed }">
      <div class="brand">
        <img class="brand-logo" :src="logoUrl" alt="漫剧AI" />
        <span v-show="!collapsed" class="brand-text">漫剧AI</span>
      </div>

      <nav class="nav">
        <router-link
          v-for="item in menus"
          :key="item.path"
          :to="item.path"
          class="nav-item"
          :class="{ active: isActive(item.path) }"
        >
          <el-icon class="nav-icon"><component :is="item.icon" /></el-icon>
          <span v-show="!collapsed" class="nav-label">{{ item.label }}</span>
        </router-link>
      </nav>

      <div class="sidebar-footer">
        <div v-show="!collapsed" class="sidebar-decor">
          <Doodles :size="80" :rotate="-5" type="wave" :opacity="0.6" />
        </div>
        <span v-show="!collapsed" class="version">ComicDrama v1.0 </span>
      </div>
    </aside>

    <!-- 主区域 -->
    <div class="main">
      <!-- 顶栏 -->
      <header class="topbar">
        <div class="topbar-left">
          <button class="icon-btn" :title="collapsed ? '展开侧栏' : '收起侧栏'" @click="collapsed = !collapsed">
            <el-icon><Fold v-if="!collapsed" /><Expand v-else /></el-icon>
          </button>
          <span class="page-title">{{ pageTitle }}</span>
        </div>

        <div class="topbar-right">
          <!-- 主题切换 -->
          <div class="theme-switcher">
            <button
              v-for="t in themes"
              :key="t.value"
              class="theme-dot"
              :class="{ active: themeStore.current === t.value }"
              :style="{ backgroundColor: t.color }"
              :title="t.label"
              @click="themeStore.setTheme(t.value)"
            />
          </div>

          <!-- 用户菜单 -->
          <el-dropdown trigger="click" @command="handleCommand">
            <div class="user-chip">
              <el-avatar :size="32" class="user-avatar">{{ avatarText }}</el-avatar>
              <span class="user-name">{{ userStore.nickname || '用户' }}</span>
              <el-icon><CaretBottom /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile" :icon="UserFilled">个人中心</el-dropdown-item>
                <el-dropdown-item v-if="userStore.isAdmin" command="admin" :icon="Setting">
                  管理后台
                </el-dropdown-item>
                <el-dropdown-item divided command="logout" :icon="SwitchButton">
                  退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <!-- 内容区 -->
      <main class="content">
        <router-view v-slot="{ Component, route }">
          <component :is="Component" :key="route.fullPath" />
        </router-view>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Fold,
  Expand,
  CaretBottom,
  UserFilled,
  Setting,
  SwitchButton,
  DataBoard,
  DataAnalysis,
  Film,
  VideoCamera
} from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { useThemeStore, type ThemeName } from '@/stores/theme'
import { Doodles } from '@/components/illustrations'
import logoSoft from '@/assets/ComicDramaLogo-soft.png'
import logoBright from '@/assets/ComicDramaLogo-bright.png'
import logoDark from '@/assets/ComicDramaLogo-dark.png'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const themeStore = useThemeStore()

const logoMap = { soft: logoSoft, bright: logoBright, dark: logoDark }
const logoUrl = computed(() => logoMap[themeStore.current])

const collapsed = ref(false)

const menus = [
  { path: '/dashboard', label: '仪表盘', icon: DataBoard },
  { path: '/dashboard/stats', label: '数据看板', icon: DataAnalysis },
  { path: '/task', label: '我的任务', icon: Film },
  { path: '/work', label: '作品管理', icon: VideoCamera }
]

const themes: { label: string; value: ThemeName; color: string }[] = [
  { label: '柔光色系', value: 'soft', color: '#ff9f1c' },
  { label: '亮光色系', value: 'bright', color: '#f5a788' },
  { label: '暗光色系', value: 'dark', color: '#5b7a99' }
]

const pageTitle = computed(() => (route.meta.title as string) || '漫剧AI')
const avatarText = computed(() => (userStore.nickname || 'U').charAt(0).toUpperCase())

function isActive(path: string) {
  if (route.path === path) return true
  if (path === '/dashboard') return route.path === '/dashboard'
  return route.path.startsWith(path + '/')
}

async function handleCommand(cmd: string) {
  if (cmd === 'logout') {
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
  } else if (cmd === 'profile') {
    router.push('/profile')
  } else if (cmd === 'admin') {
    router.push('/admin')
  }
}
</script>

<style scoped>
.layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
}

/* ===== 侧边栏 ===== */
.sidebar {
  width: 220px;
  flex-shrink: 0;
  height: 100vh;
  overflow: hidden;
  background-color: var(--cd-bg-card);
  border-right: 1.5px solid var(--cd-border);
  display: flex;
  flex-direction: column;
  transition: width 0.22s ease;
}
.sidebar.collapsed {
  width: 64px;
}

.brand {
  height: 60px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 18px;
  border-bottom: 1.5px dashed var(--cd-border);
}
.brand-logo {
  width: 34px;
  height: 34px;
  flex-shrink: 0;
  object-fit: contain;
}
.brand-text {
  font-size: 17px;
  font-weight: 800;
  color: var(--cd-text);
  letter-spacing: 1px;
  white-space: nowrap;
}

.nav {
  flex: 1;
  padding: 14px 10px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 11px 14px;
  border-radius: 8px;
  color: var(--cd-text-secondary);
  font-weight: 600;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.15s ease;
  white-space: nowrap;
}
.nav-item:hover {
  background-color: var(--cd-bg-soft);
  color: var(--cd-text);
}
.nav-item.active {
  background-color: var(--cd-primary);
  color: #fff;
  box-shadow: 2px 2px 0 0 var(--cd-shadow);
}
.nav-icon {
  font-size: 18px;
  flex-shrink: 0;
}

.sidebar-footer {
  padding: 12px 18px;
  border-top: 1.5px dashed var(--cd-border);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}
.sidebar-decor {
  opacity: 0.5;
}
.version {
  font-size: 11px;
  color: var(--cd-text-secondary);
}

/* ===== 主区域 ===== */
.main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
}

.topbar {
  height: 60px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  background-color: var(--cd-bg-card);
  border-bottom: 1.5px solid var(--cd-border);
}
.topbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.icon-btn {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  border: 1.5px solid transparent;
  background: transparent;
  color: var(--cd-text);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  transition: background-color 0.15s ease;
}
.icon-btn:hover {
  background-color: var(--cd-bg-soft);
}
.page-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--cd-text);
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 18px;
}

.theme-switcher {
  display: flex;
  gap: 6px;
  padding: 5px 8px;
  border-radius: 20px;
  background-color: var(--cd-bg-soft);
}
.theme-dot {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  border: 2px solid transparent;
  cursor: pointer;
  transition: transform 0.15s ease, border-color 0.15s ease;
}
.theme-dot:hover {
  transform: scale(1.15);
}
.theme-dot.active {
  border-color: var(--cd-text);
  transform: scale(1.1);
}

.user-chip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 10px 4px 4px;
  border-radius: 24px;
  cursor: pointer;
  transition: background-color 0.15s ease;
}
.user-chip:hover {
  background-color: var(--cd-bg-soft);
}
.user-avatar {
  background-color: var(--cd-primary);
  color: #fff;
  font-weight: 700;
}
.user-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--cd-text);
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.content {
  flex: 1;
  min-height: 0;
  padding: 22px;
  overflow-y: auto;
}

@media (max-width: 768px) {
  .sidebar {
    width: 64px;
  }
  .brand-text,
  .nav-label,
  .version {
    display: none;
  }
  .user-name {
    display: none;
  }
}
</style>
