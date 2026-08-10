# 数据库表设计

> 漫剧AI引擎数据库共 31 张表，按业务域分为 6 类。所有表均使用 InnoDB 引擎、utf8mb4 字符集，支持逻辑删除（`deleted` 字段）。

---

## 一、表清单总览

| 业务域 | 表数量 | 表名 |
|--------|--------|------|
| 权限域 | 5 | sys_user, sys_role, sys_permission, sys_user_role, sys_role_permission |
| 系统配置 | 5 | ai_model_config, system_config, step_model_binding, prompt_template, prompt_template_version |
| 任务调度 | 5 | comic_task, task_queue, task_progress_log, task_failure_log, task_node_state |
| 流水线产物 | 8 | story_summary, storyboard, asset_design, asset_image, material_prompt, storyboard_image, storyboard_audio, scene_video |
| 作品资源 | 4 | comic_work, comic_work_timeline, resource_file, resource_cleanup_log |
| 审计统计 | 4 | operation_log, task_statistics_daily, model_token_pricing, token_usage_log |

---

## 二、权限域（5 表）

### sys_user - 用户表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| username | VARCHAR(64) | 用户名 |
| password | VARCHAR(128) | 密码（BCrypt 加密） |
| nickname | VARCHAR(64) | 昵称 |
| avatar | VARCHAR(255) | 头像 URL |
| email | VARCHAR(128) | 邮箱 |
| phone | VARCHAR(20) | 手机号 |
| status | TINYINT | 状态：0禁用 1启用 |
| create_time / update_time / deleted | - | 审计字段 |

### sys_role - 角色表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| role_code | VARCHAR(32) | 角色编码 |
| role_name | VARCHAR(64) | 角色名称 |
| description | VARCHAR(255) | 描述 |
| status | TINYINT | 状态 |

### sys_permission - 权限表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| parent_id | BIGINT | 父权限 ID |
| permission_code | VARCHAR(64) | 权限编码 |
| permission_name | VARCHAR(64) | 权限名称 |
| type | TINYINT | 类型：1菜单 2按钮 3接口 |
| path | VARCHAR(255) | 路由路径 |

### sys_user_role - 用户-角色关联表

| 字段 | 类型 | 说明 |
|------|------|------|
| user_id | BIGINT | 用户 ID |
| role_id | BIGINT | 角色 ID |

### sys_role_permission - 角色-权限关联表

| 字段 | 类型 | 说明 |
|------|------|------|
| role_id | BIGINT | 角色 ID |
| permission_id | BIGINT | 权限 ID |

---

## 三、系统配置（5 表）

### ai_model_config - AI 模型配置表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| protocol | VARCHAR(32) | **调用协议**（路由到对应 Invoker，如 openai-compatible / custom-http） |
| model_provider | VARCHAR(64) | 模型服务商（任意自定义值，用于展示/筛选） |
| model_name | VARCHAR(64) | 模型名称 |
| model_type | TINYINT | 模型类型：1文本 2图像 3音频 4视频 |
| model_capabilities | VARCHAR(255) | 能力声明（逗号分隔，如 STREAMING,IMAGE_TO_IMAGE），对应 `ModelCapability` 枚举 |
| api_url | VARCHAR(512) | API 调用地址 |
| api_key | VARCHAR(512) | API 密钥（加密存储） |
| status | TINYINT | 状态：0禁用 1启用 |
| weight | INT | 权重（多模型负载均衡，`SelectorStrategy.WEIGHTED_RANDOM` 使用） |
| selector_strategy | VARCHAR(32) | 负载均衡策略：WEIGHTED_RANDOM / ROUND_ROBIN / LEAST_USED / CONSISTENT_HASH |

> **表结构变更规范**：每次 ALTER TABLE 新增/修改/删除列，必须同步更新 `comic-drama/sql/comic_drama.sql` 中对应的 CREATE TABLE 定义，保证脚本与实际数据库一致。

### system_config - 系统配置表

通用键值对配置，支持运行时动态修改。

### step_model_binding - 步骤-模型绑定表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| step_code | VARCHAR(32) | 步骤编码（SUMMARY/STORYBOARD/...） |
| step_name | VARCHAR(64) | 步骤中文名 |
| step_order | TINYINT | 步骤顺序（1-9） |
| model_config_id | BIGINT | 关联 ai_model_config 表的 ID |
| model_type | TINYINT | 模型类型（冗余） |
| deleted | TINYINT | 逻辑删除（0未删 1已删） |

> **缓存机制**：StepModelBindingResolver 启动时 `reload()` 全量加载到内存；修改绑定后需调用 `refreshStep(stepCode)` 刷新单步骤缓存，避免全量 reload。

### prompt_template - Prompt 模板表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| template_code | VARCHAR(32) | 模板编码（SUMMARY / STORYBOARD / ASSET_DESIGN / ASSET_IMAGE / ASSET_DERIVE / IMAGE / AUDIO / VIDEO） |
| template_name | VARCHAR(64) | 模板名称 |
| current_version | INT | 当前版本号 |
| status | TINYINT | 状态 |

### prompt_template_version - Prompt 模板版本表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| template_id | BIGINT | 关联 prompt_template.id |
| version | INT | 版本号 |
| content | TEXT | Prompt 内容（使用 {{asset_desc}} / {{storyboard_desc}} 等占位符） |
| change_log | VARCHAR(255) | 变更说明 |

---

## 四、任务调度（5 表）

### comic_task - 任务主表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| user_id | BIGINT | 创建用户 ID |
| task_no | VARCHAR(32) | 业务任务编号（TASK+yyyyMMddHHmmss+4位随机） |
| title | VARCHAR(128) | 任务标题 |
| story_requirement | TEXT | 故事需求 |
| duration | INT | **剧情时长**（秒，每分镜默认值） |
| aspect_ratio | VARCHAR(16) | **画幅比例**（16:9 / 9:16 / 1:1） |
| resolution | VARCHAR(16) | **分辨率**（480p / 720p / 1080p / 2K / 4K） |
| voice_enabled | TINYINT | **是否配音**：1启用 / 0禁用（=0 时跳过步骤7） |
| art_style | VARCHAR(64) | **漫剧画风**（后端存英文值/自定义，前端中文展示） |
| visual_style | VARCHAR(64) | **漫剧风格**（后端存英文值/自定义，前端中文展示） |
| exec_mode | TINYINT | 执行模式：0全自动 / 1人工审核 |
| status | TINYINT | 任务状态：0排队 1生成中 2已完成 3失败 4已暂停 |
| current_step | INT | 当前执行步骤（1-9） |
| progress | INT | 进度百分比 0-100 |
| pending_review | TINYINT | 是否待审核（人工审核模式且当前步完成） |
| failure_step | INT | 失败步骤号 |
| failure_reason | VARCHAR(512) | 失败原因摘要 |
| failure_detail | TEXT | 失败详情堆栈 |
| total_consume_time | INT | 总耗时（秒） |
| start_time / end_time | DATETIME | 执行起止时间 |
| error_message | TEXT | 冗余错误信息（兼容） |

### task_queue - 任务队列表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| task_id | BIGINT | 关联 comic_task.id |
| status | TINYINT | 队列状态：0排队 1处理中 2完成 3失败 |
| queued_at | DATETIME | 入队时间 |
| started_at | DATETIME | 开始处理时间 |
| completed_at | DATETIME | 完成时间 |

### task_progress_log - 任务进度日志表

记录每个步骤的开始/完成/失败事件，用于实时进度推送与历史回溯。

### task_failure_log - 任务失败日志表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| task_id | BIGINT | 关联 comic_task.id |
| step_code | VARCHAR(32) | 失败步骤 |
| error_type | VARCHAR(64) | 错误类型 |
| error_message | TEXT | 错误详情 |
| stack_trace | TEXT | 堆栈信息 |
| occurred_at | DATETIME | 发生时间 |

### task_node_state - 任务节点状态表

记录任务每个步骤的执行状态、产物引用、重试次数等，是断点续跑的核心依赖。

---

## 五、流水线产物（8 表）

每个步骤对应一张产物表，结构相似（含 task_id、step_index、产物字段、create_time、update_time、deleted）。所有支持版本管理与衍生关系的表均通过 `version` / `base_xxx` / `derived_from` 字段追溯：

| 表 | 步骤 | 核心字段 |
|----|------|---------|
| story_summary | 1 故事摘要 | task_id, content（文本）, duration, word_count |
| storyboard | 2 分镜脚本 | task_id, scene_group_id, scene_index, local_seq, shot_type, location, time_of_day, characters, action, dialogue, emotion, camera_angle, camera_movement, scene, props, sound_effect, bgm, duration, visual_desc, storyboard_desc, shot_desc |
| asset_design | 3 资产设计 | task_id, asset_type, asset_name, **base_asset_name**, **derived_from**, asset_desc, resource_url, **version** |
| asset_image | 4 资产绘图 | task_id, asset_id, asset_type, asset_name, image_url, thumbnail_url, width, height, prompt_used, status |
| derive_images (asset_image 复用) | 5 衍生绘图 | asset_image.base_image_id ≠ NULL 表示衍生，关联上一版 |
| material_prompt | 5.5 素材提示词扩展 | task_id, scene_index, prompt_type, prompt_text, negative_prompt |
| storyboard_image | 6 分镜绘图 | task_id, scene_index, image_url, thumbnail_url, width, height, seed, prompt |
| storyboard_audio | 7 配音合成 | task_id, scene_index, role_name, audio_url, duration, voice_name, speed, emotion |
| scene_video | 8 视频生成 | task_id, scene_index, video_url, cover_url, duration, resolution, file_size |

> 步骤 9 视频合并产物存入 `comic_work` 表。视频生成步骤（步骤8）注入的三个关键参数：`storyboard_image`（分镜图）、`asset_images`（资产图，按资产聚合）、`audio_files`（按 storyboardId 分组的音频片段）。

### task_node_state - 任务节点状态表（重点）

记录任务每个步骤的执行状态、产物引用、重试次数、重生成次数，是**断点续跑**的核心依赖：

| 字段 | 类型 | 说明 |
|------|------|------|
| id | VARCHAR(32) | 主键（雪花/UUID） |
| task_id | BIGINT | 关联 comic_task.id |
| node_code | VARCHAR(32) | 节点编码（同 step_code） |
| node_name | VARCHAR(64) | 节点名称 |
| step | INT | 步骤顺序（1-9） |
| status | TINYINT | 节点状态：0待执行 1执行中 2成功 3失败 4批量中 5已暂停 6已跳过 |
| start_time / end_time | VARCHAR(32) | 起止时间字符串 |
| duration / duration_ms | INT | 耗时（秒 / 毫秒，短耗时展示毫秒） |
| input_payload / output_payload | TEXT | 输入/输出快照（JSON，用于断点续跑恢复上下文） |
| input_snapshot / output_snapshot | TEXT | 结构化快照 |
| error_message / error_msg | TEXT | 错误信息（双字段兼容） |
| retry_count | INT | 失败重试次数 |
| regenerate_count | INT | 人工重生成次数 |

> **断点续跑机制**：`resumeFromFailure` 调用 `findResumeStep(taskId)`，返回第一个 `status ∈ {1, 3}`（进行中 或 失败）的节点作为续跑起点。已成功节点（status=2）的产物由 ArtifactLoader 自动加载。

---

## 六、作品资源（4 表）

### comic_work - 作品表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| task_id | BIGINT | 关联任务 ID（手动创建可为空） |
| title | VARCHAR(128) | 作品标题 |
| cover_url | VARCHAR(255) | 封面 URL |
| video_url | VARCHAR(255) | 最终视频 URL |
| user_id | BIGINT | 创建用户 |
| status | TINYINT | 状态 |

### comic_work_timeline - 作品时间轴表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| work_id | BIGINT | 关联 comic_work.id |
| order_num | INT | 排序序号 |
| type | VARCHAR(32) | 条目类型（image/audio/video/text） |
| content | TEXT | 条目内容 |
| resource_url | VARCHAR(255) | 资源 URL |

### resource_file - 资源文件表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| file_name | VARCHAR(255) | 文件名 |
| file_path | VARCHAR(512) | 存储路径 |
| file_type | VARCHAR(32) | 文件类型 |
| file_size | BIGINT | 文件大小（字节） |
| storage_type | VARCHAR(16) | 存储类型（local/minio） |
| md5 | VARCHAR(32) | MD5 校验值 |

### resource_cleanup_log - 资源清理日志表

记录资源清理操作，用于审计与回滚。

---

## 七、审计统计（4 表）

### operation_log - 操作日志表

AOP 切面自动记录，含操作人、操作类型、操作内容、IP、耗时等。

### task_statistics_daily - 每日统计聚合表

| 字段 | 类型 | 说明 |
|------|------|------|
| stat_date | DATE | 统计日期 |
| total_tasks | INT | 任务总数 |
| success_tasks | INT | 成功任务数 |
| failed_tasks | INT | 失败任务数 |
| total_tokens | BIGINT | Token 总用量 |
| total_cost | DECIMAL(10,4) | 总成本（元） |

### model_token_pricing - 模型计费标准表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| model_provider | VARCHAR(64) | 模型服务商 |
| model_name | VARCHAR(64) | 模型名称 |
| input_price | DECIMAL(10,6) | 输入价格（元/千 token） |
| output_price | DECIMAL(10,6) | 输出价格（元/千 token） |

### token_usage_log - Token 用量日志表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| task_id | BIGINT | 关联任务 ID |
| step_code | VARCHAR(32) | 步骤编码 |
| model_provider | VARCHAR(64) | 模型服务商 |
| model_name | VARCHAR(64) | 模型名称 |
| input_tokens | INT | 输入 token 数 |
| output_tokens | INT | 输出 token 数 |
| cost | DECIMAL(10,6) | 本次成本（元） |
| content | TEXT | 调用内容（可选） |

---

## 八、约定

### 1. 审计字段

所有业务表继承 `BaseEntity`，统一包含：

- `create_time`：创建时间，MyBatis-Plus 自动填充
- `update_time`：更新时间，MyBatis-Plus 自动填充
- `deleted`：逻辑删除标记（0未删 1已删），MyBatis-Plus `@TableLogic` 自动处理

### 2. 主键策略

- 业务表：`IdType.ASSIGN_ID`（雪花算法，Long 类型）
- 关联表：复合主键或自增主键

### 3. 字符集

统一使用 `utf8mb4` + `utf8mb4_0900_ai_ci`，支持完整 Unicode（含 emoji）。
