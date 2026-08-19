# 部署运维手册

> 完整的环境准备、数据库初始化、编译启动与运维操作说明。

---

## 一、环境准备

### 1. 必备软件

| 软件 | 版本要求 | 验证命令 |
|------|---------|---------|
| JDK | 21+ | `java -version` |
| Maven | 3.9+ | `mvn -v` |
| MySQL | 8.0 | `mysql --version` |
| Node.js | 20 LTS | `node -v` |
| MinIO | 可选 | 默认使用本地文件存储 |

### 2. Maven 镜像配置

如遇 `Could not resolve dependencies`，可在 `~/.m2/settings.xml` 配置阿里云镜像：

```xml
<mirror>
  <id>aliyun</id>
  <mirrorOf>central</mirrorOf>
  <url>https://maven.aliyun.com/repository/public</url>
</mirror>
```

---

## 二、数据库初始化

### 1. 首次新建数据库

```sql
-- 用 root 登录 MySQL 后执行
CREATE DATABASE comic_drama DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE comic_drama;
SOURCE comic_drama.sql;   -- 或用 Navicat/DBeaver 导入
```

脚本已内置：
- 31 张业务表
- 演示账号 `admin / 123456`
- 角色、权限初始化数据
- AI 模型配置、步骤绑定、计费标准等基础数据

### 2. 从旧版本升级

如已有旧版本 `comic_drama` 数据库，按序执行迁移脚本补表/补字段：

```sql
-- Phase 5.5 新增列（衍生关系、版本号、AI 协议化）
SOURCE comic-drama/sql/migration_p5_5_protocol_and_version.sql;
-- 其他历史迁移
SOURCE comic-drama/sql/migration_add_usage_log_content.sql;
SOURCE comic-drama/sql/migration_add_deleted_to_step_model_binding.sql;
SOURCE comic-drama/sql/migration_p0_p1_tables.sql;
```

迁移失败时可选**重建路径**（开发环境推荐，省心）：
```sql
DROP DATABASE comic_drama;
CREATE DATABASE comic_drama ...;
SOURCE comic-drama/sql/comic_drama.sql;
```

> ⚠️ **表结构同步铁律**：每次修改数据库中的表（ALTER TABLE / 新增列 / 修改列 / 删除列）后，**必须同步更新** `comic-drama/sql/comic_drama.sql` 中对应的 CREATE TABLE 定义。否则会出现"脚本有定义但实际数据库缺列"或"实际数据库有列但脚本缺定义"，最终导致 MyBatis-Plus 查询时报错。

---

## 三、AI 模型配置

在管理后台（`/admin`）或直接修改 `ai_model_config` 表配置各模型的 API Key 和端点。新版采用**协议化路由**（`protocol` 字段），90% 新模型无需写 Java 代码：

| 类型 | 用途 | 推荐 protocol | 配置项 |
|------|------|--------------|--------|
| 文本 | 故事摘要、分镜脚本、资产设计 | `openai-compatible` | api_url/api_key/model_name |
| 图像 | 资产绘图、衍生绘图、分镜绘图 | `openai-compatible`（文生图）或自实现 | 额外声明 `IMAGE_TO_IMAGE` 能力 |
| 语音 | 角色配音 | 自实现 Invoker 或 `custom-http` | - |
| 视频 | 视频生成 | `agnes-video` 或 `custom-http` | api_url 需以 `/v1` 结尾（见下方说明） |

**视频模型 URL 注意事项**：`api_url` 应填写到协议基址（如 `https://apihub.agnes-ai.com/v1`），Invoker 内部会自动追加 `/videos` 路径，不需要手动重复拼接。

**快速演示路径（零配置）**：启动本地 Mock 服务，所有 AI 调用走 9876 端口：
```powershell
python .\scripts\mock_model_server.py
```
Mock 服务会对齐 Phase 5 新工作流：
- 步骤1 返回三段式 SUMMARY
- 步骤2 返回 12 列 STORYBOARD CSV（含 group_id/local_seq，资产名加 _v1 后缀）
- 步骤3 返回 6 列 ASSET_DESIGN CSV（新增 baseAssetName/derivedFrom/version，含 小雨_v2 衍生资产）

> 引擎不绑定特定模型服务商。自定义 Invoker 开发详见 [插件开发指南](../extension/plugin-dev.md)。

---

## 四、启动方式

### 方式 A：一键脚本（推荐）

```powershell
# 后端（会自动检测 target jar，缺失则 mvn 编译；按序启动 5 个服务窗口）
.\scripts\start-backend.ps1

# 前端（另开一个终端，缺失 node_modules 会自动 npm install）
.\scripts\start-frontend.ps1
```

> 脚本会为每个后端服务开独立 PowerShell 窗口，关闭窗口即停止该服务。

### 方式 B：手动启动

```powershell
# 1) 编译后端
cd .\comic-drama
mvn clean install -DskipTests "-Duser.language=en" "-Duser.country=US"

# 2) 按序启动（每个一条命令/窗口）
java -jar .\comic-gateway\target\comic-gateway-*.jar
java -jar .\comic-task-service\target\comic-task-service-*.jar
java -jar .\comic-workflow-service\target\comic-workflow-service-*.jar
java -jar .\comic-resource-service\target\comic-resource-service-*.jar
java -jar .\comic-gateway\target\comic-gateway-*.jar

# 3) 启动前端
cd ..\comic-drama-frontend
npm install
npm run dev
```

---

## 五、端到端验证

1. （可选）启动 Mock 服务：`python .\scripts\mock_model_server.py`（无需真实 AI Key）
2. 浏览器访问 `http://127.0.0.1:5170`
3. 用 `admin / 123456` 登录 → 进入仪表盘
4. 点击「创建任务」→ 必填：
   - 故事需求（≥5字）
   - **剧情时长**（秒，测试推荐填 `6`）
   - **画幅比例**（16:9 / 9:16 / 1:1）
   - **分辨率**（测试推荐 `480p`，可显著减少等待时间）
   - **是否配音**（关闭则跳过步骤7，测试推荐先关闭以减少耗时）
   - **漫剧画风 + 漫剧风格**（前端下拉选项均为中文；选择「自定义」可填任意中文描述）
   - **执行模式**：全自动 / 人工审核
5. 进入「任务详情」→ 顶部信息栏显示：剧情时长 / 画幅比例 / 分辨率 / 配音开关 / 画风+风格中文组合
6. 实时查看 9 步流水线进度和产物：
   - 节点状态颜色：灰=等待 / 蓝=进行中 / 绿=完成 / 红=失败 / 橙=批量中 / 灰蓝=已暂停 / 灰叉=已跳过
   - **人工审核模式**：每步完成后暂停，可点击「执行下一步」或「继续」批量
   - **单图重生成**：点击步骤4/5/6图片右上角按钮，可修改参数并重新生成（工作流运行时自动禁用）
7. **断点续跑**：任务失败或进行中后，点击「重试」会从第一个 status ∈ {进行中,失败} 节点恢复
8. 任务完成后进入「作品列表」查看最终漫剧
9. 顶栏可切换三套主题（柔光 / 亮光 / 暗光）
10. 资源中心（管理后台 → 资源中心）支持列表/卡片双视图，可按文件名、文件类型（图片/视频/音频/文档/其他）、任务ID筛选

---

## 六、存储切换

修改 `resource-service` 的 `application.yml` 中 `storage.type` 配置：

```yaml
storage:
  type: local  # 或 minio
  minio:
    endpoint: http://127.0.0.1:9000
    access-key: minioadmin
    secret-key: minioadmin
    bucket: comic-drama
```

---

## 七、常见运维问题

### Q: 登录提示 401？

检查 task-service（8103）与 gateway 是否已启动，以及 MySQL 中 `admin` 账号密码是否为 `123456`（SQL 已内置）。

### Q: 任务一直「排队中」不变？

检查 task-service 是否启动成功，内存队列消费者每 1s 轮询。重启后若存在未完成任务，`QueueRecoveryListener` 会自动将 FAILED / RUNNING 状态的任务恢复为 PENDING 重新入队。

### Q: 视频重生成返回空结果？

检查 workflow-service 日志中提交任务的 URL，确认 `api_url` 末尾是否已包含 `/v1`（不应出现 `.../v1/v1/videos` 双重路径）。可在管理后台 → AI 模型配置中查看并修正 `api_url`。

### Q: 资源中心「资源文件」Tab 无数据？

workflow 流水线写入中间产物表（asset_image / scene_video 等），资源中心通过 `ResourceSyncScheduler` 定时从中间表扫描回填 `resource_file` 表，启动时即触发一次全量同步。若无数据，检查 resource-service 日志中 `[ResourceSyncScheduler]` 输出。

### Q: AI 步骤执行失败？

1. 检查管理后台中对应模型的 API Key 和端点是否配置正确
2. 使用「测试连通性」功能验证模型可用性
3. 查看 `task_failure_log` 中的错误详情

### Q: WebSocket 连接失败？

前端会自动降级为 HTTP 轮询，不影响功能。如需排查，检查 gateway 8070 端口是否可访问，以及 token 是否有效。

### Q: 前端 `npm install` 报 peer 依赖冲突？

本项目已锁定 `pinia-plugin-persistedstate@^3.2.1`（兼容 pinia 2.x），如仍报错用：

```bash
npm install --legacy-peer-deps
```
