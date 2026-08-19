# 前端路由与页面映射

> 漫剧AI前端路由注册、页面组件说明与导航结构。

---

## 路由配置

路由定义位于 `src/router/index.ts`，使用 Vue Router 4 的 `createWebHistory()` 模式。

所有路由分为两类：
- **公开路由**：登录页无需认证
- **受保护路由**：需要登录态，未登录自动跳转 `/login?redirect=...`

---

## 路由映射表

| 路径 | 视图组件 | 说明 |
|------|----------|------|
| `/login` | LoginView | 登录页（公开） |
| `/profile` | ProfileView | 个人中心 |
| `/dashboard` | DashboardView | 仪表盘（首页） |
| `/dashboard/stats` | DashboardStatsView | 数据看板 |
| `/task` | TaskListView | 我的任务列表 |
| `/task/create` | TaskCreateView | 创建任务 |
| `/task/:id` | TaskDetailView | 任务详情（9步产物、重生成、人工审核） |
| `/work` | WorkListView | 作品列表 |
| `/work/:id` | WorkDetailView | 作品详情（时间轴编辑） |
| `/work/share/:token` | ShareWorkView | 作品分享页（公开） |
| `/admin` | AdminView | 管理后台容器（需 ADMIN 角色） |
| `/403` | NotFoundView | 无权限提示 |
| `*`（通配） | NotFoundView | 404 页面 |

> **注意**：管理后台子页面（用户管理/系统设置/模型配置/Prompt模板/用量统计/资源中心/系统监控）均在 `AdminView.vue` 中以 Tab 形式加载，不使用独立路由。

---

## 路由守卫

```typescript
router.beforeEach((to, _from, next) => {
  // 1. 设置页面标题
  document.title = to.meta.title ? `${to.meta.title} · 漫剧AI` : '漫剧AI生成Agent'

  // 2. 公开路由直接放行
  if (isPublic) return next()

  // 3. 未登录 → 跳登录页并记录 redirect
  if (!userStore.isLogin)
    return next({ path: '/login', query: { redirect: to.fullPath } })

  // 4. 角色守卫（预留）
  if (to.meta.roles && !userStore.hasRole(role))
    return next('/403')

  next()
})
```

---

## 管理后台 Tab 页面

`AdminView.vue` 通过 `el-tabs` 管理以下功能模块，各模块为独立 Vue 组件：

| Tab | 组件文件 | 说明 |
|-----|----------|------|
| 用户管理 | UserManagementView | 用户 CRUD、状态切换、搜索 |
| 系统设置 | SystemSettingsView | 平台参数配置 |
| 模型配置 | ModelConfigView | AI 模型 API Key/端点/协议化配置 |
| Prompt 模板 | PromptTemplateView | 各步骤 Prompt 管理 |
| 用量统计 | StatisticsView | Token 用量、计费统计 |
| 资源中心 | ResourceCenterView | 资源文件（列表/卡片双视图）+ 清理日志 |
| 系统监控 | SystemMonitorView | 服务健康、队列状态 |

---

## 导航结构

### 主导航（侧栏）

```
┌─────────────────────┐
│  漫剧AI             │
├─────────────────────┤
│  📊 仪表盘           │  → /dashboard
│  📋 我的任务         │  → /task
│  🎬 作品管理         │  → /work
│                     │
│  ——— 管理后台 ────── │  → /admin
│  🔧 系统设置         │  (tab)
│  🤖 AI模型配置       │  (tab)
│  📝 Prompt模板       │  (tab)
│  📈 用量统计         │  (tab)
│  🗂️ 资源中心         │  (tab)
│  📊 系统监控         │  (tab)
├─────────────────────┤
│  👤 个人中心         │  → /profile
│  🌙 暗光 / ☀️ 亮光 / 💡 柔光 │ 主题切换
│  🔒 退出登录         │
└─────────────────────┘
```

### 管理后台子导航

管理后台顶部有一行横向导航 Tab，对应上述各管理模块。

---

## 公共布局

所有受保护页面使用 `DashboardLayout.vue` 布局：

```
┌──────────────────────────────────────────────┐
│  Header：Logo · 搜索 · 主题切换 · 用户菜单   │
├──────────────┬───────────────────────────────┤
│              │                               │
│  SideNav     │        <router-view />        │
│  (固定宽     │     （动态内容区域）           │
│   220px）    │                               │
│              │                               │
├──────────────┴───────────────────────────────┤
│  Footer：版本信息                            │
└──────────────────────────────────────────────┘
```
