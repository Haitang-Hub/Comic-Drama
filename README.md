# 漫剧AI引擎 ComicDrama

> 漫剧AI引擎：基于 Spring Boot 3 + Vue 3 + TypeScript + MySQL 8 的分布式 AI 漫剧生产平台。集成多 AI 服务商，支持故事摘要→分镜脚本→资产设计→图像→配音→视频全链路自动生成，输出完整漫剧（含封面、成片视频、分镜图像、配音音频、资产图像）。

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-blue.svg)](https://spring.io/projects/spring-boot)
[![Vue 3](https://img.shields.io/badge/Vue-3.4+-green.svg)](https://vuejs.org/)
[![Node.js](https://img.shields.io/badge/Node.js-20+-orange.svg)](https://nodejs.org/)
[![Maven](https://img.shields.io/badge/Maven-3.9+-red.svg)](https://maven.apache.org/)
[![MySQL](https://img.shields.io/badge/MySQL-8.0+-blue.svg)](https://www.mysql.com/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

---

## 项目简介

漫剧AI引擎是一个**分布式 AI 漫剧生产平台**，能够：
- 根据故事需求自动生成分镜脚本、角色/场景资产、配音音频
- 通过 AI 图像生成和 AI 视频生成，输出完整的漫剧作品
- 支持人工审核节点控制，灵活干预生成流程
- 支持单图/单资产独立重生成，版本追溯
- 支持三种主题（柔光/亮光/暗光）

**核心特点：**
- **微服务架构**：4 个独立服务 + 统一网关，各自端口独立（Eureka 已移除，服务直连）
- **协议化模型接入**：90% 新模型只需数据库配置，无需写 Java 代码
- **三阶段流水线**：脚本生成 → 视觉资产 → 视频合成，每阶段独立上下文
- **断点续跑**：任务失败后可从第一个未完成的步骤恢复
- **人工审核**：全自动模式/人工审核模式两种执行方式

---

## 技术栈

| 层 | 技术 |
|----|------|
| **前端** | Vue 3 + TypeScript + Element Plus + Pinia + Vite |
| **网关** | Spring Cloud Gateway 4.1 + Spring Boot 3.2 + Sentinel + CORS |
| **后端** | Spring Boot 3.2.5 + Spring Cloud 2023.0.1 + Spring Security 6 + Sa-Token 1.37 |
| **ORM** | MyBatis-Plus 3.5.5 + Flyway（可选） |
| **数据库** | MySQL 8.0 |
| **认证** | JWT（jsonwebtoken）+ Sa-Token |
| **存储** | 本地文件存储（默认）/ MinIO（可选） |
| **其他** | Caffeine Cache + WebSocket + OpenFeign |

---

## 模块说明

```
comic-drama/                        # 父工程（多模块 Maven）
├── pom.xml                         # 父 POM：dependencyManagement + BOM
├── comic-common/                   # 公共组件（DTO/异常/常量/工具类）
├── comic-gateway/                  # 统一网关（Spring Cloud Gateway + Sentinel）
├── comic-task-service/             # 任务服务（认证/队列/进度/失败/统计）
├── comic-workflow-service/         # 工作流服务（9步Handler/AI调用/计费/模型配置）
├── comic-resource-service/         # 资源服务（作品/资源文件/存储）
├── sql/                            # 数据库脚本
│   ├── comic_drama.sql             # 主建库脚本（31张表 + 初始数据）
│   └── migration_*.sql            # 历史迁移脚本
├── scripts/
│   ├── start-backend.ps1           # 一键启动后端（按序启动各服务）
│   ├── start-frontend.ps1          # 一键启动前端
│   ├── restart-all.ps1             # 全量重启脚本
│   └── mock_model_server.py        # Mock AI 模型服务（联调用）
└── restart-all.ps1                 # 全量重启（停止所有 + 重新编译启动）
```

---

## 快速开始

### 环境要求

| 软件 | 版本要求 |
|------|---------|
| JDK | 21+ |
| Maven | 3.9+ |
| MySQL | 8.0 |
| Node.js | 20 LTS |
| Python 3 | 仅 mock 服务需要 |

### 一键启动

```powershell
# 1. 启动后端（自动编译 + 按序启动 4 个服务窗口）
.\scripts\start-backend.ps1

# 2. 另开终端，启动前端
.\scripts\start-frontend.ps1
```

### 手动启动

```powershell
# 1. 编译后端
cd comic-drama
mvn clean install -DskipTests "-Duser.language=en" "-Duser.country=US"

# 2. 启动各服务（每个服务单独窗口）
java -jar .\comic-gateway\target\comic-gateway-*.jar
java -jar .\comic-task-service\target\comic-task-service-*.jar
java -jar .\comic-workflow-service\target\comic-workflow-service-*.jar
java -jar .\comic-resource-service\target\comic-resource-service-*.jar

# 3. 启动前端（新终端）
cd ..\comic-drama-frontend
npm install
npm run dev
```

### 零配置演示

```powershell
# 启动本地 Mock AI 服务（所有模型调用走 localhost:9876）
python .\scripts\mock_model_server.py

# 然后按正常流程创建任务，所有 AI 步骤会用 Mock 数据返回
```

---

## 服务架构

```
                    ┌─────────────────────┐
                    │  浏览器（5170端口）   │
                    └──────────┬──────────┘
                               │
                               ▼
              ┌──────────────────────────────┐
              │   comic-gateway（8070）       │
              │  Spring Cloud Gateway        │
              │  WebSocket · CORS · Swagger  │
              └───────┬─────────┬───────────┘
                      │         │
          ┌───────────┼─┐    ┌──┼───────────┐
          ▼           ▼  │    │  ▼           ▼
    ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐
    │ task-    │ │workflow- │ │resource- │ │  MySQL   │
    │ service  │ │ service  │ │ service  │ │ (8105)   │
    │ :8103    │ │ :8104    │ │ :8105    │ │          │
    │ 任务/队列 │ │ 流水线/AI│ │ 作品/资源 │ │ 31张业务 │
    │ 认证/用户│ │ 计费/模型│ │ 清理日志  │ │ 表+数据  │
    └──────────┘ └──────────┘ └──────────┘ └──────────┘
```

> **注意**：早期版本包含 Eureka 注册中心（8761）、auth-service（8081）、system-service（8082），现已整合精简。auth 功能并入 task-service，system 功能并入 workflow-service。

---

## 端口一览

| 服务 | 端口 | 说明 |
|------|------|------|
| frontend | 5170 | Vite 开发服务器 |
| gateway | 8070 | 统一入口 |
| task-service | 8103 | 任务/认证/用户/统计 |
| workflow-service | 8104 | 流水线/AI调用/模型配置 |
| resource-service | 8105 | 作品/资源/存储 |
| mock-model | 9876 | 本地 Mock 服务（可选） |
| MySQL | 3306 | 数据库 |
| MinIO | 9000/9001 | 对象存储（可选） |

各服务 `src/main/resources/application.yml` 可修改端口。

---

## 端到端验证

1. 浏览器访问 `http://127.0.0.1:5170`
2. 登录账号：`admin / 123456`
3. 创建任务：填写故事需求、剧情时长、画幅比例、分辨率、画风风格等
4. 进入任务详情查看 9 步流水线进度（节点状态颜色标识）
5. 支持单图重生成、断点续跑、人工审核介入
6. 完成后在作品列表查看最终漫剧

---

## 文档

| 文档 | 说明 |
|------|------|
| [部署运维手册](./docs/deploy/install.md) | 环境准备、数据库初始化、启动方式、常见问题 |
| [架构设计](./docs/architecture.md) | 微服务分层、工作流原理、横切能力 |
| [数据库设计](./docs/database/table-design.md) | 31张表结构说明 |
| [端口清单](./docs/deploy/port-list.md) | 各服务端口与地址 |
| [前端路由](./docs/frontend/router-page.md) | 页面路由与组件映射 |
| [AI 协议化接入](./docs/extension/plugin-dev.md) | InvokerRegistry 使用与自定义扩展 |

---

## API 调试

所有 Swagger UI 已加入网关白名单，可直接访问：

```
http://localhost:8070/swagger-ui.html
```

调用接口需在右上角填入 `Bearer <token>` 进行鉴权。

---

## 数据库说明

首次使用请在 MySQL 中执行：

```sql
CREATE DATABASE comic_drama DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE comic_drama;
SOURCE comic-drama/sql/comic_drama.sql;
```

SQL 文件包含：建表、初始数据（admin 用户、角色、权限、模型配置、计费标准）。

---

## 许可证

MIT License
