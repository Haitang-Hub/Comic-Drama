-- =============================================================================
-- 漫剧AI生成Agent项目 · MySQL 8.0 数据库初始化脚本
-- 文件：comic_drama.sql
-- 说明：包含全部业务表结构、字段中文注释、索引
--       严格按照9步工作流设计：摘要→分镜→资产设计→资产绘图→衍生绘图→分镜绘图→配音→视频生成→视频合并
-- 字符集：utf8mb4  排序规则：utf8mb4_0900_ai_ci
-- =============================================================================

DROP DATABASE IF EXISTS `comic_drama`;
CREATE DATABASE `comic_drama` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `comic_drama`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- =============================================================================
-- 模块一：系统权限（用户 / 角色 / 权限）
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. 用户表
-- -----------------------------------------------------------------------------
CREATE TABLE `sys_user` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT          COMMENT '主键ID',
  `username`       VARCHAR(64)  NOT NULL                         COMMENT '用户名（登录账号）',
  `password`       VARCHAR(128) NOT NULL                         COMMENT '密码（BCrypt加密存储）',
  `nickname`       VARCHAR(64)           DEFAULT NULL            COMMENT '昵称',
  `avatar`         VARCHAR(512)          DEFAULT NULL            COMMENT '头像URL',
  `email`          VARCHAR(128)          DEFAULT NULL            COMMENT '邮箱',
  `phone`          VARCHAR(20)           DEFAULT NULL            COMMENT '手机号',
  `gender`         TINYINT               DEFAULT 0               COMMENT '性别：0未知 1男 2女',
  `status`         TINYINT               DEFAULT 1               COMMENT '账号状态：0禁用 1启用',
  `last_login_time` DATETIME             DEFAULT NULL            COMMENT '最后登录时间',
  `last_login_ip`  VARCHAR(64)           DEFAULT NULL            COMMENT '最后登录IP',
  `remark`         VARCHAR(255)          DEFAULT NULL            COMMENT '备注',
  `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`        TINYINT               DEFAULT 0               COMMENT '逻辑删除：0未删 1已删',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_status` (`status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表';

-- -----------------------------------------------------------------------------
-- 2. 角色表
-- -----------------------------------------------------------------------------
CREATE TABLE `sys_role` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT              COMMENT '主键ID',
  `role_code`   VARCHAR(64) NOT NULL                             COMMENT '角色编码（如 ADMIN/USER）',
  `role_name`   VARCHAR(64) NOT NULL                             COMMENT '角色名称',
  `description` VARCHAR(255)         DEFAULT NULL                COMMENT '角色描述',
  `sort`        INT                  DEFAULT 0                   COMMENT '排序号',
  `status`      TINYINT              DEFAULT 1                   COMMENT '状态：0禁用 1启用',
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP   COMMENT '创建时间',
  `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     TINYINT              DEFAULT 0                   COMMENT '逻辑删除：0未删 1已删',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色表';

-- -----------------------------------------------------------------------------
-- 3. 权限表
-- -----------------------------------------------------------------------------
CREATE TABLE `sys_permission` (
  `id`            BIGINT      NOT NULL AUTO_INCREMENT            COMMENT '主键ID',
  `parent_id`     BIGINT               DEFAULT 0                 COMMENT '父权限ID（0为顶级）',
  `perm_code`     VARCHAR(128) NOT NULL                          COMMENT '权限编码',
  `perm_name`     VARCHAR(64) NOT NULL                           COMMENT '权限名称',
  `perm_type`     TINYINT              DEFAULT 1                 COMMENT '类型：1菜单 2按钮 3接口',
  `path`          VARCHAR(255)         DEFAULT NULL              COMMENT '前端路由路径',
  `component`     VARCHAR(255)         DEFAULT NULL              COMMENT '前端组件路径',
  `icon`          VARCHAR(64)          DEFAULT NULL              COMMENT '菜单图标',
  `sort`          INT                  DEFAULT 0                 COMMENT '排序号',
  `status`        TINYINT              DEFAULT 1                 COMMENT '状态：0禁用 1启用',
  `create_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`       TINYINT              DEFAULT 0                 COMMENT '逻辑删除：0未删 1已删',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_perm_code` (`perm_code`),
  KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='权限表';

-- -----------------------------------------------------------------------------
-- 4. 用户-角色关联表
-- -----------------------------------------------------------------------------
CREATE TABLE `sys_user_role` (
  `id`          BIGINT   NOT NULL AUTO_INCREMENT                 COMMENT '主键ID',
  `user_id`     BIGINT   NOT NULL                                COMMENT '用户ID',
  `role_id`     BIGINT   NOT NULL                                COMMENT '角色ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP      COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),
  KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户-角色关联表';

-- -----------------------------------------------------------------------------
-- 5. 角色-权限关联表
-- -----------------------------------------------------------------------------
CREATE TABLE `sys_role_permission` (
  `id`            BIGINT   NOT NULL AUTO_INCREMENT               COMMENT '主键ID',
  `role_id`       BIGINT   NOT NULL                              COMMENT '角色ID',
  `permission_id` BIGINT   NOT NULL                              COMMENT '权限ID',
  `create_time`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP    COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_perm` (`role_id`, `permission_id`),
  KEY `idx_permission_id` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色-权限关联表';


-- =============================================================================
-- 模块二：AI模型与系统配置
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 6. AI模型配置表
-- -----------------------------------------------------------------------------
CREATE TABLE `ai_model_config` (
  `id`                BIGINT       NOT NULL AUTO_INCREMENT           COMMENT '主键ID',
  `model_provider`    VARCHAR(64)  NOT NULL                          COMMENT '模型服务商（任意自定义值，与 AiModelInvoker.supports() 方法匹配）',
  `model_name`        VARCHAR(64)  NOT NULL                          COMMENT '模型名称（实际调用时的模型ID）',
  `model_type`        TINYINT      NOT NULL                          COMMENT '模型类型：1文本 2图像 3音频 4视频',
  `protocol`          VARCHAR(32)          DEFAULT NULL              COMMENT '调用协议：openai-chat/modelscope-image/ark-image/ark-tts/ark-video/custom-http-*，NULL时按旧supports逻辑路由',
  `capabilities`      JSON                 DEFAULT NULL              COMMENT '能力声明JSON数组，如["STREAMING","IMAGE_TO_IMAGE"]',
  `selector_strategy` VARCHAR(32)          DEFAULT 'WEIGHTED_RANDOM' COMMENT '负载均衡策略：WEIGHTED_RANDOM/ROUND_ROBIN/LOWEST_COST/FASTEST_RESPONSE',
  `api_url`           VARCHAR(512) NOT NULL                          COMMENT 'API调用地址',
  `api_key`           VARCHAR(512) NOT NULL                          COMMENT 'API密钥（加密存储）',
  `status`            TINYINT              DEFAULT 1                 COMMENT '状态：0禁用 1启用',
  `weight`            INT          NOT NULL DEFAULT 100              COMMENT '权重（多模型负载均衡，值越大调度概率越高）',
  `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`           TINYINT              DEFAULT 0                 COMMENT '逻辑删除：0未删 1已删',
  PRIMARY KEY (`id`),
  KEY `idx_model_type` (`model_type`),
  KEY `idx_status` (`status`),
  KEY `idx_protocol` (`protocol`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI模型配置表';

-- -----------------------------------------------------------------------------
-- 7. 系统配置表
-- -----------------------------------------------------------------------------
CREATE TABLE `system_config` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT             COMMENT '主键ID',
  `config_key`   VARCHAR(128) NOT NULL                            COMMENT '配置键',
  `config_value` VARCHAR(1024)         DEFAULT NULL               COMMENT '配置值',
  `value_type`   TINYINT               DEFAULT 1                  COMMENT '值类型：1字符串 2数字 3布尔 4JSON',
  `config_name`  VARCHAR(128)          DEFAULT NULL               COMMENT '配置项中文名',
  `description`  VARCHAR(255)          DEFAULT NULL               COMMENT '配置说明',
  `is_system`    TINYINT               DEFAULT 0                  COMMENT '是否系统内置：0否 1是',
  `status`       TINYINT               DEFAULT 1                  COMMENT '状态：0禁用 1启用',
  `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
  `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统配置表';

-- -----------------------------------------------------------------------------
-- 8. 步骤-模型绑定表（关联 ai_model_config 表，工作流各步骤使用的模型）
-- -----------------------------------------------------------------------------
CREATE TABLE `step_model_binding` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT          COMMENT '主键ID',
  `step_code`       VARCHAR(32)  NOT NULL                         COMMENT '步骤编码（SUMMARY/STORYBOARD/ASSET_DESIGN/ASSET_IMAGE/ASSET_DERIVE/STORYBOARD_IMAGE/AUDIO/VIDEO）',
  `step_name`       VARCHAR(64)  NOT NULL                         COMMENT '步骤中文名',
  `step_order`      TINYINT      NOT NULL                         COMMENT '步骤顺序（1-8）',
  `model_config_id` BIGINT       DEFAULT NULL                     COMMENT '关联 ai_model_config 表的 ID',
  `model_type`      TINYINT      NOT NULL                         COMMENT '模型类型（冗余，方便查询：1文本 2图像 3音频 4视频）',
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`         TINYINT              DEFAULT 0                COMMENT '逻辑删除：0未删 1已删',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_step_code` (`step_code`),
  KEY `idx_model_type` (`model_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='步骤-模型绑定表（关联AI模型配置）';


-- =============================================================================
-- 模块三：Prompt工程管理
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 9. 提示词模板表（8阶段，对应8步工作流）
-- -----------------------------------------------------------------------------
CREATE TABLE `prompt_template` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT          COMMENT '主键ID',
  `template_code`   VARCHAR(64)  NOT NULL                         COMMENT '模板编码（唯一标识）',
  `template_name`   VARCHAR(128) NOT NULL                         COMMENT '模板名称',
  `stage`           TINYINT      NOT NULL                         COMMENT '所属阶段：1摘要 2分镜 3资产设计 4资产绘图 5衍生绘图 6分镜绘图 7配音合成 8视频生成 9视频合并',
  `content`         LONGTEXT     NOT NULL                         COMMENT '模板内容（含变量占位符）',
  `variables`       VARCHAR(1024)         DEFAULT NULL            COMMENT '变量列表（JSON数组）',
  `description`     VARCHAR(255)          DEFAULT NULL            COMMENT '模板说明',
  `current_version` INT                   DEFAULT 1               COMMENT '当前生效版本号',
  `is_enabled`      TINYINT               DEFAULT 1               COMMENT '是否启用：0禁用 1启用',
  `create_by`       BIGINT                DEFAULT NULL            COMMENT '创建人ID',
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`         TINYINT               DEFAULT 0               COMMENT '逻辑删除：0未删 1已删',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_template_code` (`template_code`),
  KEY `idx_stage` (`stage`),
  KEY `idx_is_enabled` (`is_enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='提示词模板表（8阶段：摘要/分镜/资产设计/资产绘图/衍生绘图/分镜绘图/配音合成/视频生成）';

-- -----------------------------------------------------------------------------
-- 10. 提示词模板版本表
-- -----------------------------------------------------------------------------
CREATE TABLE `prompt_template_version` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT             COMMENT '主键ID',
  `template_id`  BIGINT       NOT NULL                            COMMENT '模板ID',
  `version_no`   INT          NOT NULL                            COMMENT '版本号',
  `content`      LONGTEXT     NOT NULL                            COMMENT '该版本模板内容',
  `variables`    VARCHAR(1024)         DEFAULT NULL               COMMENT '该版本变量列表（JSON）',
  `change_log`   VARCHAR(512)          DEFAULT NULL               COMMENT '变更说明',
  `is_current`   TINYINT               DEFAULT 0                  COMMENT '是否当前生效版本：0否 1是',
  `created_by`   BIGINT                DEFAULT NULL               COMMENT '创建人ID',
  `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_template_id` (`template_id`),
  KEY `idx_version_no` (`version_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='提示词模板版本表';


-- =============================================================================
-- 模块四：漫剧任务核心
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 11. 漫剧任务表（核心表，承接用户全局参数与任务状态）
-- -----------------------------------------------------------------------------
CREATE TABLE `comic_task` (
  `id`                          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `task_no`                     VARCHAR(64)  NOT NULL                COMMENT '任务编号（业务唯一）',
  `user_id`                     BIGINT       NOT NULL                COMMENT '创建用户ID',
  `title`                       VARCHAR(255)          DEFAULT NULL   COMMENT '任务标题',
  `story_requirement`           TEXT         NOT NULL                COMMENT '故事需求（用户输入文本）',
  `duration`                    INT                   DEFAULT 60     COMMENT '剧情时长（秒）',
  `aspect_ratio`                VARCHAR(16)           DEFAULT '16:9' COMMENT '画面比例（16:9/9:16/1:1/4:3）',
  `resolution`                  VARCHAR(16)           DEFAULT '1080p' COMMENT '分辨率（720p/1080p/2k/4k）',
  `voice_enabled`               TINYINT               DEFAULT 0      COMMENT '配音开关：0关闭 1开启',
  `exec_mode`                   TINYINT               DEFAULT 0      COMMENT '执行模式：0全自动 1人工审核',
  `art_style`                   VARCHAR(32)           DEFAULT NULL   COMMENT '画风（基础视觉技法：真人/2D/3D/厚涂/水彩/像素，支持自定义）',
  `visual_style`                VARCHAR(32)           DEFAULT NULL   COMMENT '风格（美学取向：国风/新海诚/韩漫/暗黑童话/赛博朋克/日式动漫，支持自定义）',
  `status`                      TINYINT               DEFAULT 0      COMMENT '任务状态：0排队 1生成中 2已完成 3失败 4已暂停',
  `current_step`                TINYINT               DEFAULT 0      COMMENT '当前执行步骤（1-9）',
  `progress`                    INT                   DEFAULT 0      COMMENT '总体进度百分比（0-100）',
  `failure_step`                TINYINT               DEFAULT NULL   COMMENT '失败发生的步骤',
  `failure_reason`              VARCHAR(1024)         DEFAULT NULL   COMMENT '失败原因',
  `failure_detail`              TEXT                  DEFAULT NULL   COMMENT '失败详情',
  `retry_count`                 INT                   DEFAULT 0      COMMENT '重试次数',
  `queue_position`              INT                   DEFAULT NULL   COMMENT '排队位置',
  `estimated_complete_time`     DATETIME              DEFAULT NULL   COMMENT '预估完成时间',
  `start_time`                  DATETIME              DEFAULT NULL   COMMENT '开始执行时间',
  `end_time`                    DATETIME              DEFAULT NULL   COMMENT '完成/失败时间',
  `total_consume_time`          INT                   DEFAULT 0      COMMENT '总耗时（秒）',
  `cover_url`                   VARCHAR(512)          DEFAULT NULL   COMMENT '任务封面图URL',
  `final_video_url`             VARCHAR(512)          DEFAULT NULL   COMMENT '最终成片视频URL',
  `final_work_manifest`         MEDIUMTEXT            DEFAULT NULL   COMMENT '成片manifest.json(包含视频列表)',
  `remark`                      VARCHAR(255)          DEFAULT NULL   COMMENT '备注',
  `create_time`                 DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`                 DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`                     TINYINT               DEFAULT 0      COMMENT '逻辑删除：0未删 1已删',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_no` (`task_no`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_status` (`status`),
  KEY `idx_current_step` (`current_step`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='漫剧任务表';

-- -----------------------------------------------------------------------------
-- 12. 任务队列表
-- -----------------------------------------------------------------------------
CREATE TABLE `task_queue` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT           COMMENT '主键ID',
  `task_id`         BIGINT       NOT NULL                          COMMENT '任务ID',
  `user_id`         BIGINT       NOT NULL                          COMMENT '所属用户ID',
  `queue_status`    TINYINT      NOT NULL                          COMMENT '队列状态：0等待中 1执行中 2已完成 3已取消',
  `priority`        INT                   DEFAULT 100              COMMENT '优先级（数字越小优先级越高）',
  `queue_position`  INT                   DEFAULT 0                COMMENT '当前排队位置',
  `waiting_count_ahead` INT               DEFAULT 0                COMMENT '前方等待任务数',
  `estimated_wait_seconds` INT            DEFAULT 0                COMMENT '预估等待秒数',
  `enqueued_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入队时间',
  `started_time`    DATETIME              DEFAULT NULL             COMMENT '开始执行时间',
  `finished_time`   DATETIME              DEFAULT NULL             COMMENT '执行完成时间',
  `worker_node`     VARCHAR(64)           DEFAULT NULL             COMMENT '执行节点',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_id` (`task_id`),
  KEY `idx_queue_status` (`queue_status`),
  KEY `idx_priority` (`priority`, `enqueued_time`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='任务队列表';

-- -----------------------------------------------------------------------------
-- 13. 任务进度日志表
-- -----------------------------------------------------------------------------
CREATE TABLE `task_progress_log` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT            COMMENT '主键ID',
  `task_id`        BIGINT       NOT NULL                           COMMENT '任务ID',
  `step`           TINYINT      NOT NULL                           COMMENT '所属步骤（1-8）',
  `node_type`      VARCHAR(32)           DEFAULT NULL              COMMENT '节点类型',
  `node_key`       VARCHAR(64)           DEFAULT NULL              COMMENT '节点标识',
  `progress`       INT                   DEFAULT 0                 COMMENT '当前节点进度百分比（0-100）',
  `total_progress` INT                   DEFAULT 0                 COMMENT '任务总体进度百分比（0-100）',
  `message`        VARCHAR(512)          DEFAULT NULL              COMMENT '进度描述',
  `is_pushed`      TINYINT               DEFAULT 0                 COMMENT '是否已WebSocket推送：0否 1是',
  `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
  PRIMARY KEY (`id`),
  KEY `idx_task_id` (`task_id`),
  KEY `idx_step` (`step`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='任务进度日志表';

-- -----------------------------------------------------------------------------
-- 14. 任务失败日志表
-- -----------------------------------------------------------------------------
CREATE TABLE `task_failure_log` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT           COMMENT '主键ID',
  `task_id`         BIGINT       NOT NULL                          COMMENT '任务ID',
  `step`            TINYINT      NOT NULL                          COMMENT '失败步骤（1-8）',
  `node_type`       VARCHAR(32)           DEFAULT NULL             COMMENT '失败节点类型',
  `node_key`       VARCHAR(64)           DEFAULT NULL             COMMENT '失败节点标识',
  `model_name`      VARCHAR(64)           DEFAULT NULL             COMMENT '调用模型名称',
  `error_type`      VARCHAR(64)           DEFAULT NULL             COMMENT '错误类型',
  `error_message`   VARCHAR(1024)         DEFAULT NULL             COMMENT '错误简述',
  `error_stack`     TEXT                  DEFAULT NULL             COMMENT '错误堆栈/详情',
  `request_payload` LONGTEXT              DEFAULT NULL             COMMENT '失败时的请求参数',
  `response_payload` LONGTEXT              DEFAULT NULL             COMMENT 'AI返回的原始异常内容',
  `retry_count`     INT                   DEFAULT 0                COMMENT '已重试次数',
  `resolved`        TINYINT               DEFAULT 0                COMMENT '是否已解决：0未解决 1已解决',
  `resolved_time`   DATETIME              DEFAULT NULL             COMMENT '解决时间',
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '失败时间',
  PRIMARY KEY (`id`),
  KEY `idx_task_id` (`task_id`),
  KEY `idx_step` (`step`),
  KEY `idx_resolved` (`resolved`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='任务失败日志表';

-- -----------------------------------------------------------------------------
-- 15. 任务节点状态表
-- -----------------------------------------------------------------------------
CREATE TABLE `task_node_state` (
  `id`                BIGINT       NOT NULL AUTO_INCREMENT         COMMENT '主键ID',
  `task_id`           BIGINT       NOT NULL                        COMMENT '任务ID',
  `step`              TINYINT      NOT NULL                        COMMENT '所属步骤（1-8）',
  `node_type`         VARCHAR(32)  NOT NULL                        COMMENT '节点类型',
  `node_key`          VARCHAR(64)  NOT NULL                        COMMENT '节点标识',
  `node_name`         VARCHAR(128)          DEFAULT NULL           COMMENT '节点展示名称',
  `node_status`       TINYINT               DEFAULT 0              COMMENT '节点状态：0等待 1进行中 2测试成功 3测试失败 4批量中 5已暂停 6已完成 7失败',
  `start_time`        DATETIME              DEFAULT NULL           COMMENT '步骤开始时间',
  `end_time`          DATETIME              DEFAULT NULL           COMMENT '步骤结束时间',
  `duration_ms`       BIGINT                DEFAULT NULL           COMMENT '步骤耗时（毫秒）',
  `parent_node_key`   VARCHAR(64)           DEFAULT NULL           COMMENT '父节点标识',
  `content_snapshot`  LONGTEXT              DEFAULT NULL           COMMENT '节点内容快照（JSON）',
  `resource_id`       BIGINT                DEFAULT NULL           COMMENT '关联资源文件ID',
  `can_regenerate`    TINYINT               DEFAULT 1              COMMENT '是否允许单点重生成：0否 1是',
  `regenerate_count`  INT                   DEFAULT 0              COMMENT '已重生成次数',
  `last_regenerate_time` DATETIME           DEFAULT NULL           COMMENT '最后重生成时间',
  `input_snapshot`    LONGTEXT              DEFAULT NULL           COMMENT '输入快照（JSON）',
  `output_snapshot`   LONGTEXT              DEFAULT NULL           COMMENT '输出快照（JSON）',
  `error_msg`         VARCHAR(1024)         DEFAULT NULL           COMMENT '错误信息',
  `retry_count`       INT                   DEFAULT 0              COMMENT '重试次数',
  `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_node` (`task_id`, `node_key`),
  KEY `idx_task_step` (`task_id`, `step`),
  KEY `idx_node_type` (`node_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='任务节点状态表';


-- =============================================================================
-- 模块五：8步工作流中间产物
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 16. 故事摘要表（步骤1产物：摘要生成）
-- -----------------------------------------------------------------------------
CREATE TABLE `story_summary` (
  `id`            BIGINT   NOT NULL AUTO_INCREMENT                 COMMENT '主键ID',
  `task_id`       BIGINT   NOT NULL                                COMMENT '任务ID',
  `content`          LONGTEXT NOT NULL                                COMMENT '摘要内容（AI生成的故事摘要）',
  `duration`         INT               DEFAULT NULL                   COMMENT '预估时长（秒）',
  `create_time`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP      COMMENT '创建时间',
  `update_time`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_task_id` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='故事摘要表（步骤1产物）';

-- -----------------------------------------------------------------------------
-- 17. 分镜脚本表（步骤2产物：分镜生成）
-- 格式：分镜序号|本镜时长|场景分组ID|组内序号|镜头角度|镜头描述|场景|出场角色|出场道具|分镜描述|台词内容|画面描述
-- -----------------------------------------------------------------------------
CREATE TABLE `storyboard` (
  `id`                BIGINT       NOT NULL AUTO_INCREMENT        COMMENT '主键ID',
  `task_id`           BIGINT       NOT NULL                        COMMENT '任务ID',
  `seq`               INT          NOT NULL                        COMMENT '分镜序号（全局递增，从1开始）',
  `duration`          INT                   DEFAULT 3               COMMENT '本镜时长（秒）',
  `group_id`          INT          NOT NULL                        COMMENT '场景分组ID（从1开始）',
  `local_seq`         INT                   DEFAULT 1               COMMENT '组内序号（同场景组内序号，从1开始）',
  `camera_angle`      VARCHAR(64)           DEFAULT NULL            COMMENT '镜头角度（近景/远景/俯视等）',
  `shot_desc`         TEXT                  DEFAULT NULL            COMMENT '镜头描述（动作、运镜）',
  `scene`             VARCHAR(255)          DEFAULT NULL            COMMENT '场景（场景名称_版本标识，分号分隔）',
  `character`         VARCHAR(255)          DEFAULT NULL            COMMENT '出场角色（角色名称_版本标识，分号分隔，无则写"无"）',
  `props`             VARCHAR(255)          DEFAULT NULL            COMMENT '出场道具（道具名称_版本标识，分号分隔，无则写"无"）',
  `storyboard_desc`   TEXT                  DEFAULT NULL            COMMENT '分镜描述',
  `dialogue`          TEXT                  DEFAULT NULL            COMMENT '台词内容（逗号分隔，特殊情况括号标注，无则写"无"）',
  `visual_desc`       TEXT                  DEFAULT NULL            COMMENT '画面描述（不包含画风）',
  `is_edited`         TINYINT               DEFAULT 0               COMMENT '是否被用户手动编辑：0否 1是',
  `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_seq` (`task_id`, `seq`),
  KEY `idx_group_id` (`task_id`, `group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='分镜脚本表（步骤2产物）';

-- -----------------------------------------------------------------------------
-- 18. 资产设计表（步骤3产物：人物/场景/道具/音色等资产设计）
-- 格式：资产类型|资产名称（含版本标识）|基础资产名|衍生自|资产描述|版本
-- -----------------------------------------------------------------------------
CREATE TABLE `asset_design` (
  `id`                   BIGINT       NOT NULL AUTO_INCREMENT      COMMENT '主键ID',
  `task_id`              BIGINT       NOT NULL                     COMMENT '任务ID',
  `asset_type`           VARCHAR(32)  NOT NULL                     COMMENT '资产类型：人物/场景/道具/音色',
  `asset_name`           VARCHAR(128) NOT NULL                     COMMENT '资产名称（含版本标识，如 小红_换装）',
  `base_asset_name`      VARCHAR(128)          DEFAULT NULL        COMMENT '基础资产名（无版本标识，用于归组）',
  `derived_from`         VARCHAR(128)          DEFAULT NULL        COMMENT '衍生自（上一版本资产名，无则写"无"）',
  `asset_desc`           TEXT         NOT NULL                     COMMENT '资产描述（详细描述）',
  `version`              INT                   DEFAULT 1           COMMENT '版本（由于变化产生的不同版本，从1开始）',
  `resource_url`         VARCHAR(512)          DEFAULT NULL        COMMENT '资源URL（资产图片或音色样本，步骤4生成）',
  `create_time`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_task_id` (`task_id`),
  KEY `idx_asset_type` (`task_id`, `asset_type`),
  KEY `idx_asset_name` (`task_id`, `asset_name`),
  UNIQUE KEY `uk_asset_unique` (`task_id`, `asset_type`, `asset_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='资产设计表（步骤3产物：人物/场景/道具/音色）';

-- -----------------------------------------------------------------------------
-- 19. 资产图片表（步骤4产物：资产绘图，根据资产描述+上一版本图片绘制）
-- -----------------------------------------------------------------------------
CREATE TABLE `asset_image` (
  `id`               BIGINT       NOT NULL AUTO_INCREMENT          COMMENT '主键ID',
  `task_id`          BIGINT       NOT NULL                         COMMENT '任务ID',
  `asset_id`         BIGINT       NOT NULL                         COMMENT '资产设计ID',
  `asset_type`       VARCHAR(32)  NOT NULL                        COMMENT '资产类型：人物/场景/道具',
  `asset_name`       VARCHAR(128)          DEFAULT NULL            COMMENT '资产名称（冗余，便于查询）',
  `image_url`        VARCHAR(512)          DEFAULT NULL            COMMENT '生成图片URL',
  `thumbnail_url`    VARCHAR(512)          DEFAULT NULL            COMMENT '缩略图URL',
  `base_image_id`    BIGINT                DEFAULT NULL            COMMENT '基底图ID（上一版本图片）',
  `base_image_url`   VARCHAR(512)          DEFAULT NULL            COMMENT '基底图URL（上一版本图片，作为输入）',
  `prompt_used`      TEXT                  DEFAULT NULL            COMMENT '实际使用的生成提示词',
  `generate_params`  TEXT                  DEFAULT NULL            COMMENT '生成参数JSON',
  `status`           TINYINT               DEFAULT 0               COMMENT '状态：0待生成 1已生成 2已编辑 3生成失败',
  `width`            INT                   DEFAULT NULL            COMMENT '图片宽度（px）',
  `height`           INT                   DEFAULT NULL            COMMENT '图片高度（px）',
  `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_asset_id` (`asset_id`),
  KEY `idx_task_id` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='资产图片表（步骤4产物：人物/场景/道具资产绘图）';

-- -----------------------------------------------------------------------------
-- 20. 分镜画面表（步骤5产物：分镜绘图，根据分镜描述+资产图片绘制）
-- -----------------------------------------------------------------------------
CREATE TABLE `storyboard_image` (
  `id`               BIGINT       NOT NULL AUTO_INCREMENT          COMMENT '主键ID',
  `task_id`          BIGINT       NOT NULL                         COMMENT '任务ID',
  `storyboard_id`    BIGINT       NOT NULL                         COMMENT '分镜ID',
  `image_url`        VARCHAR(512)          DEFAULT NULL            COMMENT '生成图片URL',
  `thumbnail_url`    VARCHAR(512)          DEFAULT NULL            COMMENT '缩略图URL',
  `base_image_id`    BIGINT                DEFAULT NULL            COMMENT '基底图ID',
  `prompt_used`      TEXT                  DEFAULT NULL            COMMENT '实际使用的生成提示词',
  `character_refs`   VARCHAR(512)          DEFAULT NULL            COMMENT '参考人物资产ID列表（逗号分隔）',
  `scene_refs`       VARCHAR(512)          DEFAULT NULL            COMMENT '参考场景资产ID列表（逗号分隔）',
  `prop_refs`        VARCHAR(512)          DEFAULT NULL            COMMENT '参考道具资产ID列表（逗号分隔）',
  `generate_params`  TEXT                  DEFAULT NULL            COMMENT '生成参数JSON',
  `status`           TINYINT               DEFAULT 0               COMMENT '状态：0待生成 1已生成 2已编辑 3生成失败',
  `regenerate_count` INT                   DEFAULT 0               COMMENT '重生成次数',
  `width`            INT                   DEFAULT NULL            COMMENT '图片宽度（px）',
  `height`           INT                   DEFAULT NULL            COMMENT '图片高度（px）',
  `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_storyboard_id` (`storyboard_id`),
  KEY `idx_task_id` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='分镜画面表（步骤5产物）';

-- -----------------------------------------------------------------------------
-- 21. 分镜配音表（步骤6产物：配音合成）
-- -----------------------------------------------------------------------------
CREATE TABLE `storyboard_audio` (
  `id`               BIGINT       NOT NULL AUTO_INCREMENT          COMMENT '主键ID',
  `task_id`          BIGINT       NOT NULL                         COMMENT '任务ID',
  `storyboard_id`    BIGINT       NOT NULL                         COMMENT '分镜ID',
  `audio_url`        VARCHAR(512)          DEFAULT NULL            COMMENT '音频URL',
  `text`             TEXT                  DEFAULT NULL            COMMENT '合成文本（台词）',
  `voice_asset_id`   BIGINT                DEFAULT NULL            COMMENT '绑定音色资产ID（asset_design.id）',
  `emotion`          VARCHAR(64)           DEFAULT NULL            COMMENT '情绪标签',
  `speed`            INT                   DEFAULT 50              COMMENT '语速（0-100）',
  `emotion_intensity` INT                  DEFAULT 50              COMMENT '情绪强度（0-100）',
  `duration`         DECIMAL(10,2)         DEFAULT NULL            COMMENT '音频时长（秒）',
  `status`           TINYINT               DEFAULT 0               COMMENT '状态：0待生成 1已生成 2已编辑 3生成失败',
  `regenerate_count` INT                   DEFAULT 0               COMMENT '重生成次数',
  `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_storyboard_id` (`storyboard_id`),
  KEY `idx_task_id` (`task_id`),
  KEY `idx_voice_asset` (`voice_asset_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='分镜配音表（步骤6产物）';

-- -----------------------------------------------------------------------------
-- 22. 场景视频表（步骤7产物：视频生成，按场景分组生成）
-- -----------------------------------------------------------------------------
CREATE TABLE `scene_video` (
  `id`                BIGINT       NOT NULL AUTO_INCREMENT         COMMENT '主键ID',
  `task_id`           BIGINT       NOT NULL                        COMMENT '任务ID',
  `scene_group_id`    BIGINT       NOT NULL                        COMMENT '场景分组ID',
  `video_url`         VARCHAR(512)          DEFAULT NULL           COMMENT '视频URL',
  `thumbnail_url`     VARCHAR(512)          DEFAULT NULL           COMMENT '视频封面URL',
  `base_frame_url`    VARCHAR(512)          DEFAULT NULL           COMMENT '首帧基准图URL',
  `prev_video_id`     BIGINT                DEFAULT NULL           COMMENT '上一段视频ID（用于帧承接）',
  `prev_video_last_frame` VARCHAR(512)       DEFAULT NULL           COMMENT '上一段视频最后一帧URL（作为本段首帧参考）',
  `storyboard_ids`    VARCHAR(512)          DEFAULT NULL           COMMENT '包含的分镜ID列表',
  `storyboard_seq_range` VARCHAR(64)          DEFAULT NULL           COMMENT '分镜序号区间（如 1-5）',
  `frame_count`       INT                   DEFAULT 0              COMMENT '帧数',
  `duration`          DECIMAL(10,2)         DEFAULT NULL           COMMENT '视频时长（秒）',
  `resolution`        VARCHAR(16)           DEFAULT NULL           COMMENT '分辨率',
  `generate_params`   TEXT                  DEFAULT NULL           COMMENT '生成参数JSON',
  `status`            TINYINT               DEFAULT 0              COMMENT '状态：0待生成 1已生成 2已编辑 3生成失败',
  `regenerate_count`  INT                   DEFAULT 0              COMMENT '重生成次数',
  `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_task_id` (`task_id`),
  KEY `idx_scene_group_id` (`scene_group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='场景视频表（步骤7产物）';

-- -----------------------------------------------------------------------------
-- 23. 作品表（步骤8产物：视频合并后的最终作品归档）
-- -----------------------------------------------------------------------------
CREATE TABLE `comic_work` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT            COMMENT '主键ID',
  `work_no`        VARCHAR(64)  NOT NULL                           COMMENT '作品编号（业务唯一）',
  `task_id`        BIGINT       NOT NULL                           COMMENT '来源任务ID',
  `user_id`        BIGINT       NOT NULL                           COMMENT '所属用户ID',
  `title`          VARCHAR(255)          DEFAULT NULL              COMMENT '作品标题',
  `description`    VARCHAR(512)          DEFAULT NULL              COMMENT '作品简介',
  `cover_url`      VARCHAR(512)          DEFAULT NULL              COMMENT '作品封面URL',
  `video_url`      VARCHAR(512)          DEFAULT NULL              COMMENT '合并后成片视频URL',
  `segment_count`  INT                   DEFAULT 0                 COMMENT '合并的视频段数',
  `merged_from`    VARCHAR(1024)         DEFAULT NULL              COMMENT '合并来源的场景视频ID列表（逗号分隔）',
  `duration`       INT                   DEFAULT 0                 COMMENT '作品总时长（秒）',
  `resolution`     VARCHAR(16)           DEFAULT NULL              COMMENT '分辨率',
  `aspect_ratio`   VARCHAR(16)           DEFAULT NULL              COMMENT '画面比例',
  `file_size`      BIGINT                DEFAULT 0                 COMMENT '成片文件大小（字节）',
  `status`         TINYINT               DEFAULT 1                 COMMENT '状态：1正常 2已归档 3已删除',
  `is_public`      TINYINT               DEFAULT 0                 COMMENT '是否公开：0私有 1公开',
  `view_count`     INT                   DEFAULT 0                 COMMENT '浏览次数',
  `like_count`     INT                   DEFAULT 0                 COMMENT '点赞次数',
  `publish_time`   DATETIME              DEFAULT NULL              COMMENT '发布时间',
  `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
  `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`        TINYINT               DEFAULT 0                 COMMENT '逻辑删除：0未删 1已删',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_work_no` (`work_no`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_task_id` (`task_id`),
  KEY `idx_status` (`status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='作品表（步骤8产物：视频合并后归档）';

-- -----------------------------------------------------------------------------
-- 24. 作品时间线表（场景组分镜回放顺序）
-- -----------------------------------------------------------------------------
CREATE TABLE `comic_work_timeline` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT            COMMENT '主键ID',
  `work_id`         BIGINT       NOT NULL                           COMMENT '作品ID',
  `scene_group_id`  BIGINT               DEFAULT NULL               COMMENT '场景组ID',
  `storyboard_id`   BIGINT               DEFAULT NULL               COMMENT '分镜ID',
  `video_url`       VARCHAR(1024)         DEFAULT NULL              COMMENT '场景视频URL',
  `order_index`     INT                 DEFAULT 0                   COMMENT '播放顺序（从1开始）',
  `duration`        INT                 DEFAULT 0                   COMMENT '片段时长（秒）',
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_work_id` (`work_id`),
  KEY `idx_scene_group_id` (`scene_group_id`),
  KEY `idx_storyboard_id` (`storyboard_id`),
  KEY `idx_order_index` (`order_index`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='作品时间线表（场景组分镜回放顺序）';

-- -----------------------------------------------------------------------------
-- 24b. 素材提示词表（步骤4产物：人物/场景/道具/音色长期变化）
-- -----------------------------------------------------------------------------
CREATE TABLE `material_prompt` (
  `id`                   BIGINT       NOT NULL AUTO_INCREMENT            COMMENT '主键ID',
  `task_id`              BIGINT       NOT NULL                           COMMENT '任务ID',
  `material_type`        TINYINT      NOT NULL                           COMMENT '素材类型：1人物 2场景 3道具 4音色 5其他',
  `material_code`        VARCHAR(64)  NOT NULL                           COMMENT '素材编码（如 char_main_char01）',
  `material_name`        VARCHAR(128)          DEFAULT NULL              COMMENT '素材名称',
  `prompt_content`       LONGTEXT              DEFAULT NULL              COMMENT 'AI绘图/配音提示词',
  `reference_image_url`  VARCHAR(1024)         DEFAULT NULL              COMMENT '参考图URL',
  `voice_sample_url`     VARCHAR(1024)         DEFAULT NULL              COMMENT '音色样本URL',
  `start_storyboard_seq` INT                 DEFAULT NULL                COMMENT '起始分镜序号',
  `end_storyboard_seq`   INT                 DEFAULT NULL                COMMENT '结束分镜序号',
  `is_long_term`         TINYINT              DEFAULT 0                  COMMENT '是否长期素材：0否 1是',
  `predecessor_id`       BIGINT               DEFAULT NULL               COMMENT '前置素材ID（继承/变化关系）',
  `create_time`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_task_id` (`task_id`),
  KEY `idx_material_type` (`material_type`),
  KEY `idx_material_code` (`material_code`),
  KEY `idx_long_term` (`is_long_term`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='素材提示词表（步骤4产物：人物/场景/道具/音色）';


-- =============================================================================
-- 模块六：资源存储管理
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 25. 资源文件表
-- -----------------------------------------------------------------------------
CREATE TABLE `resource_file` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT            COMMENT '主键ID',
  `task_id`        BIGINT                DEFAULT NULL              COMMENT '所属任务ID',
  `user_id`        BIGINT                DEFAULT NULL              COMMENT '上传/归属用户ID',
  `file_name`      VARCHAR(255) NOT NULL                           COMMENT '文件名',
  `original_name`  VARCHAR(255)          DEFAULT NULL              COMMENT '原始文件名',
  `file_type`      TINYINT      NOT NULL                           COMMENT '文件类型：1图片 2音频 3视频 4文档 5其他',
  `mime_type`      VARCHAR(128)          DEFAULT NULL              COMMENT 'MIME类型',
  `file_size`      BIGINT                DEFAULT 0                 COMMENT '文件大小（字节）',
  `width`          INT                   DEFAULT NULL              COMMENT '图片/视频宽度（px）',
  `height`         INT                   DEFAULT NULL              COMMENT '图片/视频高度（px）',
  `duration`       DECIMAL(10,2)         DEFAULT NULL              COMMENT '音视频时长（秒）',
  `bucket_name`    VARCHAR(64)  NOT NULL                           COMMENT 'MinIO bucket名称',
  `object_key`     VARCHAR(512) NOT NULL                           COMMENT 'MinIO对象key',
  `file_url`       VARCHAR(1024)         DEFAULT NULL              COMMENT '永久访问URL',
  `temp_url`       VARCHAR(1024)         DEFAULT NULL              COMMENT '临时签名URL',
  `temp_url_expire` DATETIME             DEFAULT NULL              COMMENT '临时URL过期时间',
  `md5`            VARCHAR(64)           DEFAULT NULL              COMMENT '文件MD5',
  `source_type`    VARCHAR(32)           DEFAULT NULL              COMMENT '来源类型',
  `source_node`    VARCHAR(64)           DEFAULT NULL              COMMENT '来源节点',
  `is_public`      TINYINT               DEFAULT 0                 COMMENT '是否公开：0私有 1公开',
  `expire_time`    DATETIME              DEFAULT NULL              COMMENT '资源过期时间',
  `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
  `deleted`        TINYINT               DEFAULT 0                 COMMENT '逻辑删除：0未删 1已删',
  PRIMARY KEY (`id`),
  KEY `idx_task_id` (`task_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_file_type` (`file_type`),
  KEY `idx_object_key` (`object_key`),
  KEY `idx_md5` (`md5`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='资源文件表';

-- -----------------------------------------------------------------------------
-- 26. 资源清理日志表
-- -----------------------------------------------------------------------------
CREATE TABLE `resource_cleanup_log` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT             COMMENT '主键ID',
  `resource_id`   BIGINT                DEFAULT NULL               COMMENT '被清理资源ID',
  `task_id`       BIGINT                DEFAULT NULL               COMMENT '所属任务ID',
  `file_name`     VARCHAR(255)          DEFAULT NULL               COMMENT '文件名',
  `object_key`    VARCHAR(512)          DEFAULT NULL              COMMENT 'MinIO对象key',
  `file_size`     BIGINT                DEFAULT 0                  COMMENT '释放空间大小（字节）',
  `cleanup_type`  VARCHAR(32)           DEFAULT NULL              COMMENT '清理类型',
  `cleanup_reason` VARCHAR(255)         DEFAULT NULL              COMMENT '清理原因',
  `operator`      VARCHAR(64)           DEFAULT NULL              COMMENT '操作人',
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '清理时间',
  PRIMARY KEY (`id`),
  KEY `idx_task_id` (`task_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='资源清理日志表';


-- =============================================================================
-- 模块七：操作日志与统计
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 27. 操作日志表
-- -----------------------------------------------------------------------------
CREATE TABLE `operation_log` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT             COMMENT '主键ID',
  `user_id`       BIGINT                DEFAULT NULL               COMMENT '操作人ID',
  `username`      VARCHAR(64)           DEFAULT NULL               COMMENT '操作人用户名',
  `module`        VARCHAR(64)           DEFAULT NULL               COMMENT '功能模块',
  `business_type` VARCHAR(32)           DEFAULT NULL               COMMENT '业务类型',
  `method`        VARCHAR(255)          DEFAULT NULL               COMMENT '请求方法',
  `request_url`   VARCHAR(512)          DEFAULT NULL               COMMENT '请求URL',
  `request_method` VARCHAR(10)          DEFAULT NULL               COMMENT 'HTTP方法',
  `request_param` LONGTEXT              DEFAULT NULL               COMMENT '请求参数',
  `response_data` LONGTEXT              DEFAULT NULL               COMMENT '返回结果',
  `ip`            VARCHAR(64)           DEFAULT NULL               COMMENT '操作IP',
  `location`      VARCHAR(128)          DEFAULT NULL               COMMENT '操作地点',
  `status`        TINYINT               DEFAULT 1                  COMMENT '操作状态：0失败 1成功',
  `error_msg`     VARCHAR(1024)         DEFAULT NULL               COMMENT '错误信息',
  `cost_time`     INT                   DEFAULT 0                  COMMENT '耗时（毫秒）',
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_module` (`module`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='操作日志表';

-- -----------------------------------------------------------------------------
-- 28. 任务统计表
-- -----------------------------------------------------------------------------
CREATE TABLE `task_statistics_daily` (
  `id`                  BIGINT   NOT NULL AUTO_INCREMENT             COMMENT '主键ID',
  `stat_date`           DATE     NOT NULL                            COMMENT '统计日期',
  `total_task_count`    INT               DEFAULT 0                  COMMENT '任务总数',
  `success_count`       INT               DEFAULT 0                  COMMENT '成功任务数',
  `failure_count`       INT               DEFAULT 0                  COMMENT '失败任务数',
  `success_rate`        DECIMAL(5,2)      DEFAULT 0.00              COMMENT '成功率（%）',
  `failure_rate`        DECIMAL(5,2)      DEFAULT 0.00              COMMENT '失败率（%）',
  `avg_summary_time`    INT               DEFAULT 0                  COMMENT '平均摘要耗时（秒）',
  `avg_storyboard_time` INT               DEFAULT 0                  COMMENT '平均分镜耗时（秒）',
  `avg_asset_time`      INT               DEFAULT 0                  COMMENT '平均资产设计耗时（秒）',
  `avg_image_time`      INT               DEFAULT 0                  COMMENT '平均图像生成耗时（秒）',
  `avg_audio_time`      INT               DEFAULT 0                  COMMENT '平均音频生成耗时（秒）',
  `avg_video_time`      INT               DEFAULT 0                  COMMENT '平均视频生成耗时（秒）',
  `avg_total_time`      INT               DEFAULT 0                  COMMENT '平均总耗时（秒）',
  `new_user_count`      INT               DEFAULT 0                  COMMENT '新增用户数',
  `active_user_count`   INT               DEFAULT 0                  COMMENT '活跃用户数',
  `disk_usage_bytes`    BIGINT            DEFAULT 0                  COMMENT '资源磁盘占用（字节）',
  `create_time`         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
  `update_time`         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_stat_date` (`stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='任务每日统计表';


-- =============================================================================
-- 模块八：Token用量统计
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 29. Token用量日志表（仅统计用量，不含费用）
-- -----------------------------------------------------------------------------
CREATE TABLE `token_usage_log` (
  `id`                BIGINT       NOT NULL AUTO_INCREMENT         COMMENT '主键ID',
  `task_id`           BIGINT                DEFAULT NULL            COMMENT '关联任务ID',
  `user_id`           BIGINT                DEFAULT NULL            COMMENT '用户ID',
  `step`              TINYINT               DEFAULT NULL            COMMENT '所属步骤（1-8）',
  `node_type`         VARCHAR(32)           DEFAULT NULL            COMMENT '节点类型',
  `model_name`        VARCHAR(64)  NOT NULL                        COMMENT '调用模型名称',
  `model_type`        TINYINT               DEFAULT NULL            COMMENT '模型类型：1文本 2图像 3音频 4视频',
  `latency_ms`        INT                   DEFAULT 0               COMMENT '调用耗时（毫秒）',
  `status`            TINYINT               DEFAULT 1               COMMENT '状态：0失败 1成功',
  `error_msg`         VARCHAR(512)          DEFAULT NULL            COMMENT '错误信息',
  `input_content`     MEDIUMTEXT            DEFAULT NULL            COMMENT '输入内容（完整提示词文本）',
  `output_content`    MEDIUMTEXT            DEFAULT NULL            COMMENT '输出内容（生成文本或资源URL）',
  `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_task_id` (`task_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_model_name` (`model_name`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Token用量日志表';


-- =============================================================================
-- 模块九：初始化数据
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 9.1 系统权限初始数据
-- -----------------------------------------------------------------------------
INSERT INTO `sys_role` (`role_code`, `role_name`, `description`) VALUES
('ADMIN', '系统管理员', '拥有全部权限'),
('USER',  '普通用户',   '可创建漫剧任务、管理自己的作品');

INSERT INTO `sys_user` (`username`, `password`, `nickname`, `status`) VALUES
('admin', '$2a$10$RvWn/OTXbSPZMqZ7JedcXO4mfHHtIoBpzw/uQLfxaqHCqaRLyTPK.', '系统管理员', 1);

INSERT INTO `sys_user_role` (`user_id`, `role_id`)
SELECT u.id, r.id FROM `sys_user` u, `sys_role` r
WHERE u.username='admin' AND r.role_code='ADMIN';

-- -----------------------------------------------------------------------------
-- 9.2 AI模型配置初始数据
-- 自增ID说明：1=deepseek(禁用)      2=modelscope/DeepSeek-V4-Pro(文本)
--             3=modelscope/Tongyi-Image(图像,文生图) 4=seedream(禁用)
--             5=seedance_video(视频) 6=seed_tts(音频,禁用)
--             7=modelscope/black-forest-labs/FLUX.2-klein-9B(图像,图生图)
--             8=mock/mock-test-text(文本,本地测试,127.0.0.1:9876)
--             9=agnes_video(Agnes Video V2.0 多关键帧视频, 默认禁用,使用时启用并检查API Key)
-- -----------------------------------------------------------------------------
INSERT INTO `ai_model_config` (`model_provider`, `model_name`, `model_type`, `protocol`, `capabilities`, `api_url`, `api_key`, `status`) VALUES
('deepseek', 'deepseek-v4-flash', 1, 'openai-chat', '["STREAMING","FUNCTION_CALLING","LONG_CONTEXT"]', 'https://api.deepseek.com/v1', 'yourkey', 0),
('modelscope', 'deepseek-ai/DeepSeek-V4-Pro', 1, 'modelscope-chat', '["STREAMING","FUNCTION_CALLING","LONG_CONTEXT"]', 'https://api-inference.modelscope.cn/v1', 'yourkey', 1),
('modelscope', 'Tongyi-MAI/Z-Image-Turbo', 2, 'modelscope-image', '["IMAGE_TO_IMAGE"]', 'https://api-inference.modelscope.cn/v1', 'yourkey', 1),
('seedream', 'doubao-seedream-5-0-260128', 2, 'ark-image', '["IMAGE_TO_IMAGE"]', 'https://ark.cn-beijing.volces.com/api/v3', 'yourkey', 0),
('seedance', 'doubao-seedance-2-0-mini-260615', 4, 'ark-video', '["FIRST_FRAME_LOCK"]', 'https://ark.cn-beijing.volces.com/api/v3', 'yourkey', 0),
('seed_tts', 'Seed-TTS 语音模型', 3, 'ark-tts', '["MULTI_VOICE"]', 'https://ark.cn-beijing.volces.com/api/v3', 'yourkey', 0),
('modelscope', 'black-forest-labs/FLUX.2-klein-9B', 2, 'modelscope-image', '["IMAGE_TO_IMAGE"]', 'https://api-inference.modelscope.cn/v1', 'yourkey', 1),
('mock', 'mock-test-text', 1, 'openai-chat', '["STREAMING","FUNCTION_CALLING","LONG_CONTEXT"]', 'http://127.0.0.1:9876/v1', 'mock-test-key-12345', 1),
('agnes_video', 'agnes-video-v2.0', 4, 'agnes-video', '["FIRST_FRAME_LOCK"]', 'https://apihub.agnes-ai.com', 'yourkey', 1);

-- -----------------------------------------------------------------------------
-- 9.3 步骤-模型绑定初始数据（8步工作流，步骤拆分后顺序如下）
-- model_config_id 关联上方 ai_model_config 自增ID：
--   文本类(1,2,3步)       → 2 (modelscope/DeepSeek-V4-Pro)
--   资产绘图(4步,文生图)   → 3 (modelscope/Tongyi-Image-Turbo)
--   衍生绘图(5步,图生图)   → 7 (modelscope/black-forest-labs/FLUX.2-klein-9B)
--   分镜绘图(6步)          → 7 (modelscope/black-forest-labs/FLUX.2-klein-9B)
--   音频类(7步)            → 6 (seed_tts/Seed-TTS)
--   视频类(8步)            → 5 (seedance_video/doubao-seedance)
--                            可选 9 (agnes_video/agnes-video-v2.0 多关键帧视频,需先启用)
-- -----------------------------------------------------------------------------
INSERT INTO `step_model_binding` (`step_code`, `step_name`, `step_order`, `model_config_id`, `model_type`) VALUES
('SUMMARY',          '故事摘要', 1, 8, 1),
('STORYBOARD',       '分镜脚本', 2, 8, 1),
('ASSET_DESIGN',     '资产设计', 3, 8, 1),
('ASSET_IMAGE',      '资产绘图', 4, 3, 2),
('ASSET_DERIVE',     '衍生绘图', 5, 7, 2),
('STORYBOARD_IMAGE', '分镜绘图', 6, 7, 2),
('AUDIO',            '配音合成', 7, 6, 3),
('VIDEO',            '视频生成', 8, 9, 4);

-- -----------------------------------------------------------------------------
-- 9.4 系统配置初始数据
-- -----------------------------------------------------------------------------
INSERT INTO `system_config` (`config_key`, `config_value`, `value_type`, `config_name`, `description`, `is_system`) VALUES
-- 平台通用参数
('global_ai_concurrency_limit', '20',  2, '全局AI接口并发上限', '防止打爆模型API的总并发数',         1),
('single_user_task_concurrency','2',   2, '单用户任务并发上限', '单用户同时执行的任务数',             1),
('task_max_retry_times',        '3',   2, '任务最大重试次数',   '单节点失败自动重试上限',             1),
('resource_retention_days',     '30',  2, '资源保留天数',       '过期任务资源自动清理天数',           1),
('queue_high_peak_threshold',   '50',  2, '排队高峰阈值',       '超过此等待数触发高峰提示',           1),
('websocket_heartbeat_interval','30',  2, 'WebSocket心跳间隔',  '心跳检测间隔秒数',                   1),
('websocket_reconnect_max',     '5',   2, 'WebSocket重连上限',  '断线最大重连次数',                   1),
-- AI调用全局参数
('ai_timeout',                 '120', 2, 'AI调用超时时间(秒)',  'AI模型单次调用最大等待时间',        1),
('ai_retry_times',             '3',   2, 'AI调用重试次数',      'AI调用失败后的自动重试次数',        1),
('ai_max_concurrency',         '10',  2, 'AI最大并发数',        '系统同时进行的AI请求最大并发数',    1),
('ai_weight',                  '100', 2, 'AI负载权重',          '模型负载均衡权重（预留扩展）',      1),
('task_timeout_minutes',       '60',  2, '任务超时(分钟)',      '单个任务最大执行时间',              1),
('max_video_duration',         '300', 2, '单集最大时长(秒)',    '单个任务允许生成的最大视频时长',    1),
('daily_task_quota',           '20',  2, '每日任务配额',        '每个用户每日可提交的任务数量',      1);

-- -----------------------------------------------------------------------------
-- 9.5 Prompt模板初始数据（9阶段，对应9步工作流）
-- -----------------------------------------------------------------------------
INSERT INTO `prompt_template` (`template_code`, `template_name`, `stage`, `content`, `variables`, `description`, `current_version`, `is_enabled`) VALUES
-- 步骤1：摘要生成（同时生成正/负面提示词，供步骤4/5/6使用）
('summary', '故事摘要生成模板', 1,
 '故事需求：{{story_requirement}}\n预估时长：{{duration}}秒\n画风+风格（视觉定位）：{{art_style}}+{{visual_style}}\n根据以上故事需求和预估时长，按照预估时长合理规划故事长短，编写详细的故事摘要，摘要中没有时间分配。',
 '["story_requirement","duration","art_style","visual_style"]', '步骤1：故事摘要生成', 1, 1),

-- 步骤2：分镜生成
('storyboard', '分镜脚本生成模板', 2,
 '故事摘要：{{summary}}\n故事总时长：{{duration}}秒\n根据以上故事摘要和总时长合理分配，按照 "分镜序号（全局递增，从 1 开始）|本镜时长（秒）|场景分组 ID（从 1 开始）|组内序号（同场景组内序号，从 1 开始）|镜头角度（近景 / 远景 / 俯视等）|镜头描述（动作、运镜）|场景（格式：场景名称_版本标识）|出场角色（分号分隔，没有写”无“，格式：角色名称_版本标识）|出场道具（分号分隔，没有写”无“，格式：道具名称_版本标识）|分镜描述（场景/角色/道具要写完整名称）|台词内容（分号分隔，按时间顺序，没有写”无“）|画面描述（场景/角色/道具要写完整名称，不包含画风）" 格式要求生成分镜脚本（人物必须使用具体名称，单个分镜最多不能超过15秒，分镜以秒为单位不使用小数，每个镜头的台词和动作复杂度决定时长，台词中要包含人物和语气，不能长时间没有台词，同一场景/角色/道具名称不要变化，只有“推动剧情发展”或“被角色反复使用”的道具才写入，单个分镜出现的不作为道具，版本标识是资产的永久性/结构性改变，如场景的季节变、结构变，角色的换衣、换发、年龄变、昼夜切换、道具的功能变、形态变、颜色变，版本标识需要是一个简洁的词），使用|分割字段，不要包含表头。',
 '["summary","duration"]', '步骤2：分镜脚本生成', 1, 1),

-- 步骤3：资产设计
('asset_design', '资产设计模板', 3,
 '分镜脚本：{{storyboards}}\n根据以上分镜脚本按照 "资产类型（人物/场景/道具/音色等）|资产名称（资产名称_版本标识）|基础资产名（无版本标识，用于归组）|衍生自（上一版本资产名，没有写”无“）|资产描述（资产的详细描述）|版本（由于变化产生的不同版本，从 1 开始）" 格式生成人物/场景/道具/音色等资产脚本（只在一个分镜中出现的人物/场景/道具/音色等不需要生成资产脚本，音色资产名称和基础资产名必须保持一致，版本号不为1时资产描述需要描述和上一版本的区别），使用|分割字段，不要包含表头。',
 '["storyboards"]', '步骤3：资产设计', 1, 1),

-- 步骤4：资产绘图（文生图，首次生成资产图片）
('asset_image', '资产绘图模板', 4,
 '资产描述：{{asset_desc}}\n画风+风格（视觉定位）：{{art_style}}+{{visual_style}}\n根据以上内容生成资产图片',
 '["asset_desc","art_style","visual_style"]', '步骤4：资产绘图（文生图）', 1, 1),

-- 步骤5：衍生绘图（图生图，基于上一版本资产图片生成衍生版本）
('asset_derive', '衍生绘图模板', 5,
 '上一版本资产图片：{{base_image}}\n新的资产描述：{{asset_desc}}\n画风+风格（视觉定位）：{{art_style}}+{{visual_style}}\n根据以上内容生成衍生资产图片',
 '["base_image","asset_desc","art_style","visual_style"]', '步骤5：衍生绘图（图生图）', 1, 1),

-- 步骤6：分镜绘图
('storyboard_image', '分镜绘图模板', 6,
 '画面描述：{{visual_desc}}\n角色外观描述（必须严格保持人物外观一致）：\n{{character_descriptions}}\n参考资产图片：{{asset_images}}\n画风+风格（视觉定位）：{{art_style}}+{{visual_style}}\n根据以上内容生成分镜图片，角色外观必须与描述完全一致',
 '["visual_desc","character_descriptions","asset_images","art_style","visual_style"]', '步骤6：分镜绘图', 1, 1),

-- 步骤7：配音合成（暂时不使用）
('audio', '配音合成模板', 7,
 '音色资产描述：{{voice_asset}}\n台词内容：{{dialogue}}\n根据以上内容生成配音文件，用于绑定人物音色。',
 '["voice_asset","dialogue"]', '步骤7：配音合成（暂时禁用）', 1, 0),

-- 步骤8：视频生成
('video', '视频生成模板', 8,
 '分镜脚本：{{storyboards}}\n分镜图片：{{storyboard_image}}\n配音文件：{{audio_files}}\n画风+风格（视觉定位）：{{art_style}}+{{visual_style}}\n视频比例：{{aspect_ratio}}\n分镜时长：{{duration}}秒\n请根以上内容生成分镜视频。',
 '["storyboards","storyboard_image","audio_files","art_style","visual_style","aspect_ratio","duration"]', '步骤8：视频生成', 1, 1);

SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================================
-- 脚本执行完毕
-- 共 29 张表 + 初始化数据
-- 模块结构：
--   模块一：系统权限（1-5）      sys_user / sys_role / sys_permission / sys_user_role / sys_role_permission
--   模块二：AI模型与配置（6-8）   ai_model_config / system_config / step_model_binding
--   模块三：Prompt工程（9-10）    prompt_template / prompt_template_version
--   模块四：任务核心（11-15）     comic_task / task_queue / task_progress_log / task_failure_log / task_node_state
--   模块五：工作流产物（16-24）   story_summary ~ comic_work_timeline（步骤1-9产物）
--   模块六：资源存储（25-26）     resource_file / resource_cleanup_log
--   模块七：日志统计（27-28）     operation_log / task_statistics_daily
--   模块八：Token用量统计（29）   token_usage_log
--   模块九：初始化数据            系统权限 / AI模型 / 步骤绑定 / 系统配置 / Prompt模板
-- 9步工作流：摘要生成→分镜生成→资产设计→资产绘图→衍生绘图→分镜绘图→配音合成→视频生成→视频合并
-- =============================================================================

-- =============================================================================
-- 10. 测试数据：重置任务为进行中状态（用于重新测试完整工作流）
-- 使用方法：取消注释后执行，将指定任务重置为 status=1（进行中/生成中）
-- =============================================================================

-- 将测试任务重置为进行中（从头开始执行）
-- UPDATE comic_task
-- SET status = 1, current_step = 0, progress = 0,
--     failure_step = NULL, failure_reason = NULL, failure_detail = NULL,
--     end_time = NULL
-- WHERE id = 2084235527391272961;

-- 重置任务队列为运行中
-- UPDATE task_queue
-- SET queue_status = 1, started_time = NOW(), finished_time = NULL
-- WHERE task_id = 2084235527391272961;

-- 清除节点状态（让任务从头执行）
-- DELETE FROM task_node_state WHERE task_id = 2084235527391272961;

-- 清除进度日志
-- DELETE FROM task_progress_log WHERE task_id = 2084235527391272961;