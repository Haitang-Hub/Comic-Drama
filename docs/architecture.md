# 架构设计

> 本章节详细说明漫剧AI引擎的整体架构、微服务分层、核心设计原理及横切能力。

---

## 一、整体架构

```
┌─────────────────────────────────────────────────────────┐
│                        前端（Vue 3 + TS）                  │
│                    http://127.0.0.1:5170                  │
└─────────────────────────────┬───────────────────────────┘
                              │ WebSocket + REST
                              ▼
┌─────────────────────────────────────────────────────────┐
│                   comic-gateway（8070）                   │
│         Spring Cloud Gateway · Sentinel · CORS          │
└──────┬──────────┬──────────┬──────────┬────────────────┘
       │          │          │          │
       ▼          ▼          ▼          ▼
┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────┐
│ task-    │ │workflow- │ │resource- │ │   MySQL 8.0  │
│ service  │ │ service  │ │ service  │ │  (comic_drama)│
│  :8103   │ │  :8104   │ │  :8105   │ │              │
│ 任务/队列/│ │ 流水线/   │ │ 作品/     │ │ 31张业务表   │
│ 认证/用户 │ │ AI调用/   │ │ 资源文件/ │ │ 演示账号/admin│
│ 统计     │ │ 计费/模型 │ │ 清理日志  │ │ 角色/权限数据 │
│          │ │ 配置/Prompt│ │         │ │              │
└──────────┘ └──────────┘ └──────────┘ └──────────────┘

MinIO（可选）     Local File System（默认）
 :9000/9001       F:/.../data/storage
```

---

## 二、微服务清单

> **注意**：早期版本包含 Eureka 注册中心、auth-service、system-service 三个独立服务，现已整合精简为 5 个服务（gateway + 3 业务服务 + MySQL）。auth 和 system 功能已分别并入 task-service 和 workflow-service。

| 服务 | 端口 | 职责 | 依赖外部组件 |
|------|------|------|------------|
| **comic-gateway** | 8070 | 统一入口、路由转发、CORS、Swagger 白名单、限流熔断 | Sentinel |
| **comic-task-service** | 8103 | 任务生命周期管理、内存队列调度、认证/用户管理、每日统计 | MySQL |
| **comic-workflow-service** | 8104 | 9步流水线编排、AI调用、计费审计、模型配置、Prompt模板、Schema 自动迁移 | MySQL |
| **comic-resource-service** | 8105 | 作品/时间线管理、资源文件上传下载、存储桥接、清理日志 | MySQL / MinIO / 本地文件系统 |

### 服务间调用方式

服务间通过 HTTP Feign 客户端直连调用，URL 在 `application.yml` 中硬编码（不再依赖 Eureka）：

```yaml
# workflow-service 调用 task-service
workflow:
  task-service-url: http://localhost:8103

# task-service 调用 workflow-service
workflow-client:
  url: http://localhost:8104
```

---

## 三、工作流原理

### 1. 阶段划分

工作流分为三个独立上下文阶段，每个阶段共享同一任务 ID，步骤间产物互相依赖：

```
阶段一：脚本生成（步骤 1-3）
  故事摘要 → 分镜脚本 → 资产设计
  └─ 纯文本 LLM 调用，轻量快速

阶段二：视觉资产生成（步骤 4-7）
  资产绘图 → 衍生绘图 → 分镜绘图 → 配音合成
  └─ 图像 + 语音模型调用，独立上下文

阶段三：视频生成（步骤 8-9）
  视频生成 → 视频合并
  └─ 视频模型 + 本地 ffmpeg 处理
```

### 2. 9 步执行流程

```
步骤1: story_summary   → comic_work.story_summary
步骤2: storyboard      → comic_work.storyboard
步骤3: asset_design    → comic_work.asset_design
步骤4: asset_image     → asset_image 表（角色/场景首版图）
步骤5: derivative_image → storyboard_image 表（衍生图）
步骤6: storyboard_image → storyboard_image 表（分镜图）
步骤7: dubbing         → storyboard_audio 表（音频片段）
步骤8: video           → scene_video 表（视频片段）
步骤9: video_merge     → comic_work（封面/成片/ZIP）
```

### 3. 产物版本与追溯

每个产物携带版本号和衍生关系，支持：
- 单图/单资产独立重生成
- 历史版本对比
- 全链路追溯（从故事摘要到成片）

```
asset_image.id=1, asset_id=42, version=1, derived_from=NULL   ← 首版
asset_image.id=2, asset_id=42, version=2, derived_from=1      ← 基于首版衍生
```

---

## 四、横切能力

### 1. 限流与熔断

- **Sentinel 规则**：`comic-drama-workflow-service` 配置流控规则，QPS 超限自动降级
- **Fallback**：AI 调用超时/失败时返回兜底内容，不阻断任务流

### 2. 缓存策略

- **Caffeine 本地缓存**：StepModelBindingMapper、PromptTemplateService 使用，TTL 10 分钟
- **缓存失效**：管理后台保存新配置时自动清除

### 3. 监控与告警

- **接口耗时统计**：AOP 切面记录关键接口耗时，`operation_log` 表存储
- **任务进度推送**：WebSocket 实时推送节点状态变更
- **Token 用量审计**：每次 AI 调用记录 `prompt_tokens`/`completion_tokens`/`total_tokens`

### 4. 认证与权限

- **Sa-Token + JWT**：统一鉴权框架，网关层校验 Token
- **RBAC 模型**：`sys_user` → `sys_role` → `sys_permission`，三张表实现细粒度权限控制
- **Admin Token**：管理后台接口支持 header 方式传入 `x-admin-token`，绕过 JWT

---

## 五、关键设计决策

### 为什么用工作流而非 LLM Agent？

| 维度 | Agent 长链 | 工作流引擎 |
|------|-----------|-----------|
| 上下文成本 | 所有步骤共享上下文，Token 指数增长 | 每步独立上下文，成本可控 |
| 失败恢复 | 全局重跑 | 单节点恢复 |
| 人工介入 | 黑盒执行 | 节点级审核 |
| 成本核算 | 仅总用量 | 分步骤独立记账 |
| 调试难度 | 难以定位问题步骤 | 每个步骤独立可观测 |

### 为什么协议化接入？

传统模型接入需要为每个服务商写独立 Invoker，维护成本高。协议化抽象后：
- **配置驱动**：90% 新模型只需在数据库写入配置
- **类型路由**：按 `protocol:modelType` 自动路由到对应 Invoker
- **YAML 扩展**：小众模型通过 YAML 模板驱动，无需写 Java 代码
- **测试友好**：内置 Mock Invoker，开发联调无需真实 API Key

---

## 六、目录结构

### 后端

```
comic-drama/
├── pom.xml                          # 父 POM（BOM 管理版本）
├── comic-common/                    # 公共组件（DTO/异常/常量/工具类）
├── comic-gateway/                   # 网关（路由/CORS/Sentinel/白名单）
├── comic-task-service/              # 任务服务（队列/进度/认证/用户/统计）
├── comic-workflow-service/          # 工作流服务（9步Handler/AI调用/计费/配置）
├── comic-resource-service/          # 资源服务（作品/资源文件/存储）
├── sql/                             # 数据库脚本
│   ├── comic_drama.sql              # 主脚本（31张表 + 初始数据）
│   └── migration_*.sql             # 历史迁移脚本
├── scripts/
│   ├── start-backend.ps1            # 一键启动后端（7个窗口）
│   └── start-frontend.ps1           # 一键启动前端
└── restart-all.ps1                  # 全量重启脚本
```

### 前端

```
comic-drama-frontend/
├── src/
│   ├── api/                         # 接口封装（task/admin/statistics/user/work/auth）
│   ├── views/                       # 页面组件
│   │   ├── LoginView.vue
│   │   ├── DashboardView.vue        # 仪表盘
│   │   ├── TaskListView.vue         # 任务列表
│   │   ├── TaskCreateView.vue       # 创建任务
│   │   ├── TaskDetailView.vue       # 任务详情（9步产物/重生成/人工审核）
│   │   ├── WorkListView.vue         # 作品列表
│   │   ├── WorkDetailView.vue       # 作品详情（时间轴编辑）
│   │   ├── AdminView.vue            # 管理后台容器
│   │   ├── ProfileView.vue          # 个人中心
│   │   └── admin/                   # 管理后台子页
│   │       ├── ResourceCenterView.vue  # 资源中心
│   │       └── SystemMonitorView.vue   # 系统监控
│   ├── layouts/DashboardLayout.vue  # 侧栏+顶栏布局
│   ├── styles/themes/               # 三主题（soft/bright/dark）
│   └── composables/useTaskProgress.ts  # 进度组合式函数
```

---

> 详细接口文档见 [API 参考](./api-reference.md)（Swagger 页面），部署指南见 [docs/deploy/install.md](./deploy/install.md)。
