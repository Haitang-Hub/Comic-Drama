<div align="center">
  <img src="./comic-drama-frontend/src/assets/ComicDramaLogo.png" alt="漫剧AI引擎" width="120" />
  <h1>ComicDrama 漫剧AI</h1>
  <p><b>标准化多媒体 AI 生产引擎，一站式完成文本 / 绘图 / 配音 / 分镜 / 成片全链路可管控流水线</b></p>
  <p>
    <img src="https://img.shields.io/badge/Java-21-red" alt="Java 21" />
    <img src="https://img.shields.io/badge/SpringBoot-3.2.5-green" alt="SpringBoot" />
    <img src="https://img.shields.io/badge/Vue-3.5-42b883" alt="Vue 3" />
    <img src="https://img.shields.io/badge/TypeScript-5-3178c6" alt="TypeScript" />
    <img src="https://img.shields.io/badge/MySQL-8.0-blue" alt="MySQL" />
    <img src="https://img.shields.io/badge/License-MIT-yellow" alt="License" />
  </p>
  <p>
    <a href="./README.md">中文</a> · 
    <a href="./docs/architecture.md">架构</a> · 
    <a href="./docs/deploy/install.md">部署</a> · 
    <a href="./docs/extension/plugin-dev.md">扩展</a>
  </p>
</div>

> 已落地漫剧商业生成场景，支持节点级断点恢复、逐阶段人工审核、分环节 Token 用量审计与成本核算，稳定承载批量成片任务。

---

## 一、项目分析

### 1. 行业痛点

原生 LLM 长链 Agent 做多媒体生成存在三大顽疾：

- **上下文无限膨胀**：9 步链路全部塞进单一 Agent 上下文，Token 成本指数级飙升
- **一损俱损**：单步失败需从零重跑，前置成果作废，长任务交付不可控
- **黑盒执行**：无法人工介入审核、无法分环节核算成本、无法独立管控各模型权限

通用工作流框架（如 ComfyUI、LangFlow）偏重单一模态，缺乏文本/图像/语音/视频一体化编排、成片合并、资源持久化、计费审计的端到端能力。

### 2. 竞品对比

| 方案 | 上下文隔离 | 节点级断点恢复 | 人工审核节点 | 多媒体全链路 | 分环节 Token 计费 |
|------|:---:|:---:|:---:|:---:|:---:|
| 单 Agent 长链 | ❌ 全局上下文 | ❌ 全局重跑 | ❌ | 弱 | 仅总用量统计 |
| **ComicDrama 引擎** | ✅ 步骤独立上下文 | ✅ 单节点恢复 | ✅ 逐阶段审核 | ✅ 文本/图/音/视频一体 | ✅ 每步独立记账 |

### 3. 量化收益

商用批量漫剧生产场景下：

- 节点上下文隔离机制减少约 **65%~82%** Token 消耗
- 单任务失败恢复无需重跑前置步骤，平均交付时长缩短 **40%**
- 人工审核模式大幅降低成片返工率
- 资源资产沉淀复用机制，降低重复生成成本

---

## 二、核心能力

- **9 步多媒体工作流**：文本 → 视觉资产 → 音视频渲染，三段独立上下文
- **双模式调度**：全自动批量生产 + 人工审核精品制作（支持"完成此阶段"+"执行下一步"细粒度控制）
- **节点级断点恢复**：支持从「进行中」或「失败」步骤续跑，单图/单资产可独立重生成并修改参数
- **协议化 AI 接入**：按协议类型（openai-compatible / 自定义 HTTP）动态路由 Invoker，90% 新模型仅需配置
- **分环节 Token 计费**：每次 AI 调用独立记账，支持模型定价配置与成本聚合
- **实时进度推送**：WebSocket 双通道 + HTTP 轮询降级
- **多模型负载均衡**：权重随机 / 轮询 / 最少使用 / 一致性哈希 4 种策略
- **版本化资产生成**：资产/分镜/视频均带版本标识与衍生关系，支持回溯与对比
- **插件化扩展**：自定义步骤 / AI Invoker / 存储后端 / 任务队列

---

## 三、底层工作流设计原理

### 1. 四大设计思想

- **分阶段解耦**：文本、视觉、音视频分层隔离，各步骤独立会话，互不污染上下文
- **故障域隔离**：单个绘图/配音失败仅重跑当前节点，前置产物持久化复用
- **双模式调度**：全自动 + 人工审核，适配批量生产与精品制作
- **资源资产沉淀**：角色、场景素材全局复用，降低重复生成成本

### 2. 执行链路

```mermaid
flowchart TD
    A[用户输入: 故事需求] --> B[阶段一: 脚本生成]
    B --> B1[故事摘要] --> B2[分镜脚本] --> B3[资产设计]
    
    B3 --> C[阶段二: 资产生成]
    C --> C1[资产绘图] --> C2[衍生绘图] --> C3[分镜绘图] --> C4[配音合成] 
    
    C4 --> D[阶段三: 视频生成]

    D --> E[阶段四: 视频合并]
    
    style B fill:#e1f5fe
    style C fill:#f3e5f5
    style D fill:#fff3e0
    style E fill:#e8f5e9
```

> 完整架构设计、微服务分层与横切能力详见 [架构设计文档](./docs/architecture.md)。

---

## 四、流水线业务模型：9 步多媒体生成链路

| 步骤 | 名称 | 类型 | 核心能力 |
|------|------|:---:|------|
| 1 | 故事摘要 | 文本 | 从用户需求生成故事大纲（version + derivedFrom 追溯） |
| 2 | 分镜脚本 | 文本 | 拆分为场景分组（group_id + local_seq），含镜头/台词/角色/时长 |
| 3 | 资产设计 | 文本 | 为角色/场景/道具/音色设计视觉描述，带基础资产名 + 衍生关系 |
| 4 | 资产绘图 | 图像 | 文生图，生成首版角色/场景图片（baseImageId 为空） |
| 5 | 衍生绘图 | 图像 | 图生图，基于上一版本生成衍生版本（含 version 递增） |
| 6 | 分镜绘图 | 图像 | 为每个分镜生成画面，支持按单图独立重生成改描述 |
| 7 | 配音合成 | 语音 | 为每个分镜合成角色配音，voiceEnabled=0 时自动跳过 |
| 8 | 视频生成 | 视频 | 为每个分镜生成视频片段（注入资产图 + 音频 + 分镜图） |
| 9 | 视频合并 | 视频 | 合并所有片段为最终漫剧，产物进入 comic_work 作品表 |

**创建任务时可选参数**：

| 字段 | 中文说明 | 可选值示例 |
|------|---------|-----------|
| 剧情时长 duration | 每分镜默认时长（秒） | 6 / 8 / 12 |
| 画幅比例 aspectRatio | 视频宽高比 | 16:9 / 9:16 / 1:1 |
| 分辨率 resolution | 视频分辨率 | 480p / 720p / 1080p / 2K / 4K |
| 是否配音 voiceEnabled | 是否启用步骤 7 配音 | 1 启用 / 0 禁用（跳过步骤7） |
| 漫剧画风 artStyle | 基础视觉技法（中文显示） | 真人 / 2D / 3D / 厚涂 / 水彩 / 像素 |
| 漫剧风格 visualStyle | 美学调性（中文显示） | 国风 / 新海诚 / 韩漫 / 暗黑童话 / 赛博朋克 / 日式动漫 |
| 执行模式 execMode | 调度策略 | 0 全自动 / 1 人工审核 |

**模型可插拔**：文本 / 图像 / 语音 / 视频四类模型均通过统一 Invoker 抽象接入，可在 `ai_model_config` 表中配置任意 OpenAI 兼容服务或自定义模型，详见 [插件开发指南](./docs/extension/plugin-dev.md)

---

## 五、技术架构总览

引擎按业务域垂直拆分为 7 个微服务，支持独立扩容（AI 绘图高峰可单独扩容 workflow-service）：

| 层级 | 模块 | 职责 |
|------|------|------|
| **接入层** | gateway · auth-service | 统一入口、Sa-Token 鉴权、WebSocket 推送、前端 |
| **工作流调度层** | task-service · workflow-service | 任务生命周期、9 步 Handler 编排、真实 AI 调用 |
| **资源与 AI 网关层** | system-service · resource-service | 模型配置、MinIO 存储、Token 计费审计 |

**核心技术栈**：Java 21 · SpringBoot 3.2.5 · SpringCloud · MyBatis-Plus · Sa-Token + JWT · Sentinel · Caffeine · Vue 3.5 · TypeScript · Element Plus · TailwindCSS · Pinia

> 完整微服务清单、端口分布、横切能力详见 [架构设计文档](./docs/architecture.md)。

---

## 六、界面预览

引擎提供三套主题与双执行模式，覆盖批量生产与精品制作两类场景。

### 1. 三主题样式

<table>
  <tr>
    <td align="center"><img src="./docs/images/theme-bright.png" alt="明亮主题" width="280"/><br/><sub>明亮主题 · 默认</sub></td>
    <td align="center"><img src="./docs/images/theme-dark.png" alt="暗黑主题" width="280"/><br/><sub>暗黑主题</sub></td>
    <td align="center"><img src="./docs/images/theme-soft.png" alt="柔和主题" width="280"/><br/><sub>柔和主题</sub></td>
  </tr>
</table>

### 2. 双执行模式

<table>
  <tr>
    <td align="center"><img src="./docs/images/workflow-auto.png" alt="全自动批量模式" width="420"/><br/><sub>全自动批量模式 · 一键跑通 9 步</sub></td>
    <td align="center"><img src="./docs/images/workflow-review.png" alt="人工审核模式" width="420"/><br/><sub>人工审核模式 · 逐阶段把关</sub></td>
  </tr>
</table>

> 更多截图详见 [docs/images/](./docs/images/)。

---

## 七、快速部署

```powershell
# 0) (可选) 启动本地 Mock 模型服务，用于演示/联调，无需真实 AI Key
python .\scripts\mock_model_server.py     # 监听 9876 端口

# 1) 初始化数据库（首次）
mysql -uroot -p123456 -e "CREATE DATABASE comic_drama DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
mysql -uroot -p123456 comic_drama < .\comic-drama\sql\comic_drama.sql

# 2) 编译后端
cd .\comic-drama
mvn clean install -DskipTests "-Duser.language=en" "-Duser.country=US"

# 3) 启动后端（一键脚本，自动开 7 个服务窗口）
cd ..
.\scripts\start-backend.ps1

# 4) 启动前端（另开终端）
.\scripts\start-frontend.ps1

# 5) 访问 http://127.0.0.1:5170 ，使用 admin / 123456 登录
```

> 测试任务推荐配置：**剧情时长 6s + 分辨率 480p**，可显著减少等待时间与资源消耗。
> 完整部署手册（环境依赖、SQL 初始化、AI 模型配置、运维问题）详见 [部署手册](./docs/deploy/install.md)。

---

## 八、扩展开发

引擎内核与漫剧业务解耦，可通过扩展点适配其他 AI 生产流水线场景（小说生成、短视频脚本、PPT 自动化等）：

| 扩展点 | 接口 | 适用场景 |
|--------|------|---------|
| 自定义步骤 Handler | `AbstractStepHandler` | 新增/替换流水线步骤 |
| 自定义 AI Invoker | `AiModelInvoker` | 接入任意模型服务（Midjourney、OpenAI 等） |
| 自定义存储后端 | `StorageService` | 接入 OSS / COS 等对象存储 |
| 自定义任务队列 | `TaskQueue` | 替换为 RabbitMQ / Kafka 等分布式 MQ |

示例：接入 OpenAI 兼容服务

```java
@Component
public class OpenAiCompatibleInvoker implements AiModelInvoker {
    @Override
    public boolean supports(String modelProvider) {
        return "openai".equalsIgnoreCase(modelProvider);
    }
    
    @Override
    public AiInvokeResponse invoke(AiInvokeRequest request) {
        // 调用 OpenAI Compatible API
        return AiInvokeResponse.builder()
            .content(extractContent(resp))
            .inputTokens(extractInputTokens(resp))
            .build();
    }
}
```

> 完整插件开发教程、扩展点 API、打包复用方式详见 [插件开发指南](./docs/extension/plugin-dev.md)。

---

## 九、配套文档索引

| 文档 | 路径 | 内容 |
|------|------|------|
| 架构设计 | [docs/architecture.md](./docs/architecture.md) | 微服务分层、工作流原理、横切能力 |
| 部署手册 | [docs/deploy/install.md](./docs/deploy/install.md) | 环境依赖、SQL 初始化、启动脚本、运维 |
| 服务端口 | [docs/deploy/port-list.md](./docs/deploy/port-list.md) | 全服务端口清单与 Swagger 地址 |
| 数据库设计 | [docs/database/table-design.md](./docs/database/table-design.md) | 31 张表字段说明 |
| 插件开发 | [docs/extension/plugin-dev.md](./docs/extension/plugin-dev.md) | 自定义步骤 / Invoker / 存储 / 队列 |
| 前端路由 | [docs/frontend/router-page.md](./docs/frontend/router-page.md) | 14+ 页面路由与目录结构 |
| 界面预览 | [docs/images/](./docs/images/) | 三主题样式 · 双执行模式截图（见第六章） |

---

## 十、FAQ

**Q: 引擎是否支持非漫剧类 AI 流水线？**

支持。引擎内核与漫剧业务解耦，通过自定义步骤 Handler + AI Invoker 可适配任意 AI 生产场景，如小说生成、短视频脚本、PPT 自动化等。详见 [插件开发指南](./docs/extension/plugin-dev.md)。

**Q: 如何接入第三方自定义图像/语音模型？**

90% 场景无需编码：在 `ai_model_config` 表填入 `protocol`（如 `openai-compatible`）+ `api_url` + `api_key`，引擎会通过 `InvokerRegistry` 按 `protocol:modelType` 自动路由。小众模型可通过 `CustomHttpInvoker` 配置 YAML 请求模板 + JSONPath 响应解析。

**Q: 人工审核数据如何持久化、支持二次修改？**

每个步骤产物独立存储在对应产物表（如 `story_summary`、`storyboard`），审核状态记录在 `task_node_state` 表。**单图/单资产重生成**不会影响其他步骤产物，仅重置当前节点；工作流运行时重生成按钮自动禁用，避免并发冲突。

**Q: 任务失败/进行中后如何恢复？**

调用 `POST /api/task/{id}/resume-from-failure` 接口，`findResumeStep` 会自动定位第一个「进行中(status=1)」或「失败(status=3)」的步骤作为续跑起点，`ArtifactLoader` 加载前序步骤产物，无需重跑已完成步骤。

**Q: 切换 MinIO / 本地存储？**

修改 `resource-service` 的 `application.yml` 中 `storage.type`（`minio` 或 `local`），MinIO 需配置 endpoint / accessKey / secretKey / bucket。

**Q: 漫画画风 + 风格如何使用中文自定义？**

在 [任务创建页](/task/create) 选择「自定义」即可填入任意中文描述，任务详情顶部信息栏会以中文显示（如「真人 + 国风」）；后端字段 `artStyle` / `visualStyle` 保存用户选择或自定义的原始值。

---

<div align="center">
  <p>Project First Author — <a href="https://github.com/Haitang-Hub">Haitang-Hub</a></p>
  <p>如果本项目对您有帮助，欢迎 Star ⭐ 支持</p>
</div>
