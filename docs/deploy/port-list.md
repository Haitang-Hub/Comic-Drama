# 服务端口清单

> 漫剧AI引擎各服务端口分布与说明。所有 Swagger 页面已加入网关白名单，无需登录即可浏览。

## 后端服务

| 服务 | 端口 | Swagger 地址 | 说明 |
|------|------|--------------|------|
| comic-gateway | 8070 | - | **统一入口**，前端代理目标，含 WebSocket / Swagger 白名单 |
| comic-task-service | 8103 | http://localhost:8103/swagger-ui.html | 任务核心/队列/进度/失败/节点状态 / 认证/用户管理/统计 |
| comic-workflow-service | 8104 | http://localhost:8104/swagger-ui.html | 流水线编排/9步Handler/AI调用/计费/模型配置/Prompt模板 |
| comic-resource-service | 8105 | http://localhost:8105/swagger-ui.html | 作品/时间线/资源文件/清理日志 |

> 所有 Swagger 页面调用接口需在右上角「Authorize」填入 `Bearer <token>`。

## 辅助服务

| 服务 | 端口 | 说明 |
|------|------|------|
| frontend | 5170 | Vite 开发服务器 |
| mock-model | 9876 | 本地 Mock 文本模型服务（用于演示/联调） |
| MySQL | 3306 | 数据库（默认 root / 123456） |
| MinIO | 9000/9001 | 对象存储（可选，默认使用本地文件存储） |

## 端口冲突排查

如果启动时端口被占用，可使用以下命令查找占用进程：

```powershell
# Windows
netstat -ano | findstr :8070
netstat -ano | findstr :8103
netstat -ano | findstr :8104
netstat -ano | findstr :8105

# 终止占用进程（PID 替换为实际值）
taskkill /PID <PID> /F
```

各服务的端口可在对应模块的 `src/main/resources/application.yml` 中修改。
