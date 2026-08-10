# 前端路由与页面明细

> 漫剧AI引擎前端共 14+ 个页面，基于 Vue 3.5 + Element Plus + TailwindCSS 实现，支持柔光/亮光/暗光三主题。

---

## 一、路由清单

| 页面 | 路由 | 说明 |
|------|------|------|
| 登录 | `/login` | 账号密码登录 |
| 仪表盘 | `/dashboard` | 任务概览 + 统计数据 + 快捷创建入口 |
| 仪表盘统计 | `/dashboard/stats` | 每日统计趋势 + 步骤耗时分析 |
| 任务列表 | `/tasks` | 分页查询 + 状态筛选 + 批量操作 |
| 创建任务 | `/task/create` | 故事需求 + **剧情时长/画幅比例/分辨率/是否配音/漫剧画风+风格** + 执行模式选择 |
| 任务详情 | `/task/:id` | 进度条 + 9 步产物展示 + 顶部信息栏（时长/画幅/分辨率/配音/画风+风格中文）+ 人工审核（执行下一步/继续/完成此阶段）+ 单图重生成（改参数）+ 断点续跑 |
| 作品列表 | `/works` | 已完成漫剧浏览 + 手动创建作品 + 作品编辑 |
| 作品详情 | `/work/:id` | 作品信息 + 时间轴条目增删改 + 一键重排 |
| 管理后台 | `/admin` | AI 模型/Prompt 模板（协议化）/系统配置/操作日志/角色权限/资源中心/系统监控 |
| 个人中心 | `/profile` | 个人信息 + 修改密码 + 头像 |

### 管理后台子页

| 子页 | 路由 | 说明 |
|------|------|------|
| 角色权限 | `/admin/role-permission` | 用户/角色/权限 RBAC 管理 |
| 资源中心 | `/admin/resource-center` | 资源文件浏览与管理 |
| 系统监控 | `/admin/system-monitor` | 服务状态/任务队列/性能指标监控 |

---

## 二、前端技术栈

| 分类 | 选型 |
|------|------|
| 框架 | Vue 3.5 + Vite 5 + TypeScript 5 |
| UI | Element Plus 2.8 + 绘画本速写风格 CSS |
| 状态 | Pinia 2（持久化） |
| 路由 | Vue Router 4 |
| 请求 | Axios（统一拦截/鉴权注入） |
| 样式 | TailwindCSS 3 + CSS 变量三主题 |
| 实时 | WebSocket 客户端（自动重连 + 心跳 + 降级） |

---

## 三、目录结构

```
comic-drama-frontend/
├── public/                     # 静态资源（favicon / Logo 图片）
├── src/
│   ├── api/                    # axios 封装 + 各业务接口
│   │   ├── request.ts          # 请求实例 + 拦截器 + 错误处理
│   │   ├── auth.ts             # 认证接口（登录/注册/个人信息）
│   │   ├── task.ts             # 任务接口（创建/分页/详情/暂停/继续/重试/重生成节点/重生成单图/断点续跑/审批/执行下一步）
│   │   ├── admin.ts            # 管理后台接口（模型配置/步骤绑定/Prompt模板/操作日志/角色权限/系统监控）
│   │   ├── statistics.ts       # 统计接口（每日统计/模型用量/步骤耗时）
│   │   ├── user.ts             # 用户接口
│   │   └── work.ts             # 作品接口（作品CRUD/时间轴条目）
│   ├── assets/                 # 项目资源图片（含三主题 Logo）
│   ├── components/
│   │   └── illustrations/      # 手绘风插画组件（6 个：Doodles/Marker/NotebookDesk/Pencil/SpiralNotebook/StickyNote）
│   ├── composables/
│   │   └── useTaskProgress.ts  # 任务进度组合式函数（WebSocket + HTTP轮询降级 + 自动重连）
│   ├── constants/
│   │   └── task.ts             # 状态码/步骤名映射/ART_STYLES中文/VISUAL_STYLES中文/画幅/分辨率常量
│   ├── layouts/
│   │   └── DashboardLayout.vue # 仪表盘布局（侧栏+顶栏+主题切换+路由内容区，含key避免复用）
│   ├── views/                  # 页面组件
│   │   ├── admin/              # 管理后台子页（角色权限/资源中心/系统监控）
│   │   ├── LoginView.vue
│   │   ├── DashboardView.vue
│   │   ├── DashboardStatsView.vue
│   │   ├── TaskListView.vue
│   │   ├── TaskCreateView.vue  # 创建任务：表单校验 + 画风风格中文下拉 + 自定义输入 + 执行模式说明
│   │   ├── TaskDetailView.vue  # 任务详情：9步节点/顶部信息栏中文显示/单图重生成对话框/人工审核横幅
│   │   ├── WorkListView.vue
│   │   ├── WorkDetailView.vue
│   │   ├── AdminView.vue
│   │   ├── ProfileView.vue
│   │   └── NotFoundView.vue
│   ├── stores/                 # Pinia 状态
│   │   ├── user.ts             # 用户状态（持久化）
│   │   └── theme.ts            # 主题状态（持久化，CSS变量切换）
│   ├── styles/
│   │   ├── themes/             # 三主题样式
│   │   │   ├── soft.css        # 柔光主题（默认）
│   │   │   ├── bright.css      # 亮光主题
│   │   │   └── dark.css        # 暗光主题
│   │   ├── main.css            # 全局样式
│   │   └── sketch-vars.scss    # 速写风格变量 + Macaron 纯色配色（无阴影无渐变）
│   ├── utils/
│   │   └── ws.ts               # WebSocket 客户端封装（自动重连 + 心跳 + 指数退避）
│   ├── router/
│   │   └── index.ts            # 路由配置 + 守卫（登录态/权限校验）
│   ├── App.vue
│   └── main.ts
├── index.html
├── package.json
├── vite.config.ts              # Vite 配置 + 代理到 gateway 8070
├── tailwind.config.ts
├── postcss.config.js
├── tsconfig.json
└── tsconfig.node.json
```

---

## 四、三主题切换

主题基于 CSS 变量实现，通过 Pinia 管理当前主题，所有页面通过 CSS 变量自适应：

```typescript
// stores/theme.ts
export type ThemeName = 'soft' | 'bright' | 'dark'
export const useThemeStore = defineStore('theme', {
  state: () => ({ current: 'soft' }),
  actions: {
    setTheme(theme: ThemeName) {
      this.current = theme
      document.documentElement.setAttribute('data-theme', theme)
      localStorage.setItem('cd_theme', theme)
    }
  }
})
```

主题切换通过顶栏按钮触发，状态持久化到 localStorage。

---

## 五、WebSocket 实时进度

任务详情页通过 WebSocket 接收实时进度推送：

```typescript
// composables/useTaskProgress.ts
const { progress, status, artifacts } = useTaskProgress(taskId)
```

特性：
- 自动重连（指数退避）
- 心跳保活
- 连接失败自动降级为 HTTP 轮询（不影响功能）

---

## 六、手绘风插画组件

项目内置 6 个独立的手绘风插画组件，用于空状态、加载状态等场景：

| 组件 | 用途 |
|------|------|
| `Doodles` | 涂鸦装饰 |
| `Marker` | 马克笔图标 |
| `NotebookDesk` | 笔记本桌面 |
| `Pencil` | 铅笔图标 |
| `SpiralNotebook` | 螺旋笔记本 |
| `StickyNote` | 便利贴 |
