# 技术架构设计

> 本文档完整描述漫剧AI引擎的微服务分层、工作流核心机制、双执行模式原理与故障域隔离设计。

---

## 一、整体架构分层

漫剧AI引擎采用「按业务域垂直拆分」的微服务架构，支持各业务模块独立扩容。例如 AI 绘图任务高峰时，可单独扩容 `workflow-service`，而不影响鉴权与任务调度。

引擎分为三层：

```
┌─────────────────────────────────────────────────────────────┐
│  接入层                                                       │
│  Gateway(8070) · Sa-Token 鉴权 · WebSocket 实时推送 · 前端    │
└────────────────────────┬────────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────────┐
│  工作流调度层                                                 │
│  task-service(8103)   任务生命周期 / 队列 / 断点恢复          │
│  workflow-service(8104)  9步 Handler 编排 / 真实 AI 调用      │
└────────────────────────┬────────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────────┐
│  资源与 AI 网关层                                             │
│  system-service(8102)  AI 模型配置 / Prompt / Token 计费      │
│  resource-service(8105)  MinIO 存储 / 作品 / 签名 URL         │
│  auth-service(8101)     用户 / 角色 / 权限 (RBAC)             │
└─────────────────────────────────────────────────────────────┘
```

### 微服务模块清单

| 模块 | 端口 | 职责 |
|------|------|------|
| comic-eureka | 8761 | 注册中心（单节点，可换 Nacos） |
| comic-gateway | 8070 | 统一入口、鉴权、WebSocket 代理、Sentinel 限流 |
| comic-auth-service | 8101 | 登录/注册/用户·角色·权限管理 |
| comic-system-service | 8102 | AI 模型配置/Prompt 模板/系统配置/操作日志/统计 |
| comic-task-service | 8103 | 任务核心/队列/进度/失败/节点状态 |
| comic-workflow-service | 8104 | 9 步 Handler + 真实 AI 调用 + 计费 |
| comic-resource-service | 8105 | 作品/时间线/资源文件/MinIO 签名 URL |
| comic-common | - | 公共能力：响应/异常/BaseEntity/枚举/存储/广播/AI 抽象/队列 |

---

## 二、工作流核心设计思想

### 1. 分阶段解耦

将 9 步生成链路按业务语义分为三段，各段独立会话、互不污染上下文：

- **阶段一 · 文本生成**：故事摘要 → 分镜脚本 → 资产设计
- **阶段二 · 视觉资产**：资产绘图 → 衍生绘图 → 分镜绘图
- **阶段三 · 音视频渲染**：配音合成 → 视频生成 → 视频合并

每步 AI 调用使用独立上下文，前置产物以结构化数据形式注入，避免长链 Agent 的上下文无限膨胀。

### 2. 故障域隔离

- 单个绘图/配音步骤失败，仅重跑当前节点，前置产物持久化复用
- 任务级断点恢复：从失败步骤恢复执行，`ArtifactLoader` 自动加载前序步骤产物
- 节点级重生成：可对单个步骤重新生成，重置该步骤及后续节点状态

### 3. 双模式调度

| 模式 | exec_mode | 行为 |
|------|-----------|------|
| 全自动 | 0 | 9 步连续执行，可随时「暂停」保留完整产物，从断点续跑 |
| 人工审核 | 1 | 每步完成后自动暂停，支持：①「执行下一步」单步推进 ②「继续」批量跑完后续步骤 ③「完成此阶段」等待当前步骤跑完后再暂停（保证产物完整） |

**细粒度控制按钮语义**：

| 按钮 | 触发时机 | 行为 |
|------|---------|------|
| 暂停 (PAUSE) | 排队/生成中 | 设置计划暂停标记，下一个步骤边界处进入 PAUSED |
| 继续 (RESUME) | 已暂停 | execMode=0 → 全自动跑完；execMode=1 → 执行下一步后再暂停 |
| 执行下一步 | 人工审核暂停 | 仅执行下一单步，完成后立即暂停 |
| 完成此阶段 | 生成中 | 当前步骤执行完毕后进入 PAUSED，保留完整产物供审核 |
| 重试/断点续跑 | 失败/进行中 | `findResumeStep()` 从第一个 status ∈ {1, 3} 节点恢复执行 |

**人工审核 + 单图重生成流程**：

1. 步骤执行完成 → 任务状态 `PAUSED`，`pendingReview=true`
2. 审核各步骤产物，不满意可点击单张图右上角「重新生成」，支持修改描述/画风/风格参数
3. 工作流运行时（QUEUE/RUNNING）重新生成按钮自动禁用并给出提示，避免并发冲突
4. 单图重生成仅重置对应节点，其他步骤产物完整保留

### 4. 协议化 AI 接入 + InvokerRegistry

所有 AI 模型通过 `protocol` 字段路由到对应 Invoker 实现，做到「90% 新模型接入仅需配置」：

```
InvokerRegistry
  key = protocol:modelType     (如 "openai-compatible:2" = 协议:图像模型)
  value = AiModelInvoker Bean
    ├─ OpenAiCompatibleInvoker    ← 通用 Chat/Image 兼容接口
    ├─ ArkImageInvoker            ← 字节方舟图像协议
    ├─ CustomHttpInvoker          ← YAML 驱动 + JSONPath 解析
    └─ ...
```

关键设计：
- **协议层抽象**：按 `protocol`（而非服务商）实现 Invoker，避免每加一家厂商写一个类
- **能力校验**：每个模型在 `ai_model_config.model_capabilities` 上声明 `STREAMING` / `IMAGE_TO_IMAGE` 等能力，Handler 执行前校验匹配性
- **O(1) 路由**：`InvokerRegistry` 使用 `Map<String, AiModelInvoker>`，key 为 `protocol:modelType`，替代 O(n) 遍历

### 5. 步骤-模型绑定缓存 + 版本化资产生成

**StepModelBindingResolver 缓存机制**：
- 构造时 `reload()` 全量加载 `step_model_binding` 表，`bindingCache` 常驻内存
- `createBinding / updateBinding / clearBinding / batchUpdate` 等修改操作调用 `refreshStep(stepCode)` 刷新对应步骤缓存（非全量 reload）
- 刷新日志：`刷新步骤绑定: stepCode=SUMMARY -> provider=xxx, model=yyy`

**版本化资产生成（Version + 衍生关系）**：

| 产物表 | 版本字段 | 衍生关系字段 | 说明 |
|--------|---------|-------------|------|
| asset_design | version | baseAssetName / derivedFrom | 如 "小雨_v2" 衍生自 "小雨_原版" |
| asset_image | - (关联 asset_design) | baseImageId | 首版图 baseImageId=null，衍生图关联上一版 |
| storyboard_image | - | - (sceneIndex 维度重生成) | 单图重生成 updateById 覆盖旧记录 |
| scene_video | - | - (sceneIndex 维度) | 每分镜一个视频片段 |

### 6. 资源资产沉淀

角色、场景素材在 `asset_image` 表中持久化，后续步骤（衍生绘图、分镜绘图）可复用已生成资产，避免重复生成。

---

## 三、模板方法模式

`AbstractStepHandler` 定义标准执行流程：

```
预处理（参数校验/上下文加载）
   ↓
调用 AI（统一 Invoker 抽象）
   ↓
后处理（产物格式化/资源上传）
   ↓
存产物（写入对应表 + 更新节点状态）
```

各步骤 Handler 只需实现 `doExecute`，无需关心调度、断点、审计等通用逻辑。

---

## 四、批量执行机制

批量步骤（4-8）采用「测试优先批量执行」策略：

1. 先执行第一条测试数据
2. 测试成功后批量生成剩余数据
3. 单项失败保留已成功项，仅重跑失败项

该机制在保证质量的同时最大化批量任务成功率。

---

## 五、关键横切能力

| 能力 | 实现 |
|------|------|
| 实时进度推送 | Gateway 原生 WebSocket `/ws/task/{taskId}`，前端自动重连 + 心跳 + HTTP 轮询降级 |
| 任务队列 | 内存任务队列（默认）/ RocketMQ 适配器（可选） |
| 限流降级 | Sentinel：task-submit QPS 10、ai-call QPS 5 |
| 鉴权 | Sa-Token + JWT（无状态），角色/权限写入 token extra |
| 对象存储 | MinIO + 本地文件双实现，可通过配置切换 |
| 缓存 | Caffeine 本地缓存（system-service 配置缓存 + StepModelBindingResolver 步骤绑定缓存） |
| 跨服务事件 | ApplicationEvent 广播（可换 RocketMQ） |
| 负载均衡策略 | 权重随机 / 轮询 / 最少使用 / 一致性哈希（ModelSelectorFactory） |
| 单图/单资产重生成 | `regenerateAssetImage` / `regenerateStoryboardImage` 异步接口，修改参数后仅覆盖当前记录 |

---

## 六、扩展点

引擎内核与漫剧业务解耦，可通过以下扩展点适配其他 AI 流水线场景：

- **自定义步骤 Handler**：继承 `AbstractStepHandler`，实现 `doExecute`
- **自定义 AI Invoker**：实现 `AiModelInvoker` 接口，接入任意模型服务
- **自定义存储后端**：实现 `StorageService` 接口
- **自定义队列实现**：实现 `TaskQueue` 接口

详见 [插件开发指南](./extension/plugin-dev.md)。
