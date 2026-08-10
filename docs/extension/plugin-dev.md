# 插件开发指南

> 漫剧AI引擎内核与漫剧业务解耦，可通过扩展点适配其他 AI 生产流水线场景（如小说生成、短视频脚本、PPT 自动化等）。本文档介绍四种核心扩展机制。

---

## 一、扩展点总览

| 扩展点 | 接口/基类 | 适用场景 |
|--------|----------|---------|
| 自定义步骤 Handler | `AbstractStepHandler` | 新增/替换流水线步骤 |
| 自定义 AI Invoker | `AiModelInvoker` | 接入新的模型服务 |
| 自定义存储后端 | `StorageService` | 接入其他对象存储（如 OSS/COS） |
| 自定义任务队列 | `TaskQueue` | 替换内存队列为其他 MQ（如 RabbitMQ/Kafka） |

---

## 二、自定义步骤 Handler

### 1. 设计原理

所有步骤 Handler 继承抽象类 `AbstractStepHandler`，模板方法定义标准执行流程：

```
预处理（preExecute）
   ↓
调用 AI（doExecute）  ← 子类实现
   ↓
后处理（postExecute）
   ↓
存产物（saveArtifact）
   ↓
更新节点状态（updateNodeState）
```

子类只需实现 `doExecute`，无需关心调度、断点、审计等通用逻辑。

### 2. 示例：新增「字幕生成」步骤

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class SubtitleStepHandler extends AbstractStepHandler {

    private final AiModelConfigProvider modelConfigProvider;
    
    @Override
    public WorkflowStep getStepCode() {
        return WorkflowStep.SUBTITLE;  // 需先在枚举中定义
    }

    @Override
    protected StepResult doExecute(StepContext ctx) {
        // 1. 加载前置产物（视频生成结果）
        List<SceneVideo> videos = ctx.loadArtifacts(WorkflowStep.VIDEO);
        
        // 2. 调用 AI 模型生成字幕
        AiModelContext model = modelConfigProvider.getByProviderAndType(
            "whisper", ModelType.AUDIO.getCode());
        List<Subtitle> subtitles = invokeWhisper(videos, model);
        
        // 3. 返回产物
        return StepResult.success(subtitles);
    }
}
```

### 3. 注册步骤

在 `step_model_binding` 表中配置步骤与模型的绑定关系，引擎启动时自动加载。

### 4. 步骤顺序

通过 `WorkflowStep` 枚举的 `order` 字段控制执行顺序，可在不修改代码的情况下通过配置调整。

---

## 三、自定义 AI Invoker

### 1. 协议化路由原理

AI 模型接入采用**协议层抽象**（按 `protocol` 而非服务商实现 Invoker），90% 新模型通过配置即可接入，无需编码：

```
InvokerRegistry（Map<protocol:modelType, AiModelInvoker>）
  ├─ "openai-compatible:1" → OpenAiCompatibleInvoker（文本）
  ├─ "openai-compatible:2" → OpenAiCompatibleInvoker（图像）
  ├─ "custom-http:3"       → CustomHttpInvoker（语音，YAML 驱动）
  └─ "ark-image:2"         → ArkImageInvoker（字节方舟图像协议）
```

路由匹配优先级：
1. 精确匹配 `protocol + ":" + modelType`
2. 回退匹配 `protocol + ":*"`（协议通配）
3. 最后回退 `*:*`（兜底 Invoker）

**步骤 Handler 执行前校验**：从 `ai_model_config.model_capabilities` 读取能力声明（对应 `ModelCapability` 枚举，如 STREAMING / IMAGE_TO_IMAGE / TEXT_TO_SPEECH），与当前步骤所需能力比对，不匹配则提前报错。

### 2. 接口定义

```java
public interface AiModelInvoker {
    
    /**
     * 调用 AI 模型
     * @param request 调用请求（含 prompt、参数、模型上下文）
     * @return 调用响应（含生成内容、token 用量）
     */
    AiInvokeResponse invoke(AiInvokeRequest request);
    
    /**
     * 协议匹配（新版本建议基于 protocol 字段匹配，保留 provider 兼容）
     */
    boolean supports(String protocolOrProvider);

    /** 流式调用（可选，声明 STREAMING 能力的模型需实现） */
    default StreamingAiModelInvoker streaming() {
        throw new UnsupportedOperationException("Streaming not supported");
    }
}
```

### 3. 示例 A：接入 OpenAI Compatible 服务（推荐，90% 场景覆盖）

```java
@Component
@RequiredArgsConstructor
public class OpenAiCompatibleInvoker implements AiModelInvoker {

    private final RestClient restClient;

    @Override
    public boolean supports(String protocol) {
        // 按 protocol 匹配，而非 provider
        return "openai-compatible".equalsIgnoreCase(protocol);
    }

    @Override
    public AiInvokeResponse invoke(AiInvokeRequest request) {
        AiModelContext model = request.getModelContext();
        
        // 1. 根据 modelType 路由到不同端点（chat/completions vs images/generations）
        ModelType type = ModelType.of(model.getModelType());
        String endpoint = switch (type) {
            case TEXT -> "/chat/completions";
            case IMAGE -> "/images/generations";
            default -> throw new BizException("Unsupported model type: " + type);
        };
        
        // 2. 构造请求体
        Map<String, Object> body = buildBodyByType(request, type);
        
        // 3. 调用 API
        Map<String, Object> resp = restClient.post()
            .uri(model.getApiUrl() + endpoint)
            .header("Authorization", "Bearer " + model.getApiKey())
            .body(body)
            .retrieve()
            .body(Map.class);
        
        // 4. 解析响应 + Token 用量
        return AiInvokeResponse.builder()
            .content(extractContent(resp, type))
            .inputTokens(extractToken(resp, "prompt_tokens"))
            .outputTokens(extractToken(resp, "completion_tokens"))
            .build();
    }
}
```

**数据库配置**：在 `ai_model_config` 中写入
- `protocol = 'openai-compatible'`
- `model_provider = 'doubao'`（仅用于展示/筛选）
- `model_type = 1`（文本）或 2（图像）等
- `model_capabilities = 'STREAMING'`（可选）

### 4. 示例 B：小众模型 CustomHttpInvoker（YAML 驱动，零编码）

若模型不符合 OpenAI 规范，无需写 Java 类，通过配置驱动：

1. 在 `ai_model_config.protocol = 'custom-http'`
2. 扩展配置文件中定义请求模板 + JSONPath：
```yaml
customHttp:
  templates:
    my-fancy-model:
      method: POST
      urlTemplate: "{{apiUrl}}/v1/generate"
      headers:
        Authorization: "Bearer {{apiKey}}"
      bodyTemplate: |
        {
          "prompt": "{{prompt}}",
          "size": "{{width}}x{{height}}"
        }
      responsePath: "$.data[0].url"
      inputTokenPath: "$.usage.prompt_tokens"
      outputTokenPath: "$.usage.completion_tokens"
```

### 5. 注册 Invoker

实现 `supports` 方法后，引擎通过 `InvokerRegistry` 自动扫描并注入 `Map<String, AiModelInvoker>`，无需手动注册。Spring 启动时日志会列出已加载路由：
```
[InvokerRegistry] 已注册路由: openai-compatible:1 -> OpenAiCompatibleInvoker
[InvokerRegistry] 已注册路由: custom-http:* -> CustomHttpInvoker
```

---

## 四、自定义存储后端

### 1. 接口定义

```java
public interface StorageService {
    
    String upload(MultipartFile file, String directory);
    
    String upload(byte[] content, String fileName, String directory);
    
    byte[] download(String fileUrl);
    
    void delete(String fileUrl);
    
    String getSignUrl(String fileUrl, Duration expire);
}
```

### 2. 示例：接入阿里云 OSS

```java
@Component
@ConditionalOnProperty(name = "storage.type", havingValue = "oss")
public class OssStorageService implements StorageService {

    private final OSS ossClient;
    private final String bucketName;

    @Override
    public String upload(byte[] content, String fileName, String directory) {
        String key = directory + "/" + fileName;
        ossClient.putObject(bucketName, key, new ByteArrayInputStream(content));
        return "oss://" + bucketName + "/" + key;
    }
    
    // ... 其他方法实现
}
```

### 3. 配置切换

通过 `application.yml` 的 `storage.type` 切换存储后端，已内置：
- `local`：本地文件存储（默认）
- `minio`：MinIO 对象存储

---

## 五、自定义任务队列

### 1. 接口定义

```java
public interface TaskQueue {
    
    void enqueue(TaskQueueEntry entry);
    
    TaskQueueEntry dequeue();
    
    void markCompleted(Long taskId);
    
    void markFailed(Long taskId, String errorMessage);
}
```

### 2. 内置实现

| 实现 | 适用场景 |
|------|---------|
| `InMemoryTaskQueue` | 单机部署、开发测试（默认） |
| `RocketMQTaskQueue` | 分布式部署、高吞吐场景 |

### 3. 扩展 Kafka 队列

实现 `TaskQueue` 接口，配合 `@ConditionalOnProperty` 通过配置切换即可。

---

## 六、插件打包复用

### 1. 模块化设计

将自定义扩展打包为独立 Spring Boot starter 模块：

```
comic-drama-extension-xxx/
├── src/main/java/
│   └── com/comicdrama/extension/xxx/
│       ├── config/XxxAutoConfiguration.java
│       └── handler/XxxStepHandler.java
└── src/main/resources/
    └── META-INF/
        └── spring.factories
```

### 2. spring.factories

```
org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
  com.comicdrama.extension.xxx.config.XxxAutoConfiguration
```

### 3. 引用

在其他项目中引入依赖即可启用：

```xml
<dependency>
  <groupId>com.comicdrama</groupId>
  <artifactId>comic-drama-extension-xxx</artifactId>
  <version>1.0.0</version>
</dependency>
```

---

## 七、典型扩展场景

| 场景 | 扩展方式 |
|------|---------|
| 小说连载生成 | 自定义步骤 Handler + 复用文本 Invoker |
| 短视频脚本生产 | 自定义步骤 Handler + 复用图像/视频 Invoker |
| PPT 自动化生成 | 自定义步骤 Handler + 自定义 PPT 渲染 Invoker |
| 接入 Midjourney | 自定义图像 Invoker（实现 `AiModelInvoker`） |
| 接入阿里云 OSS | 自定义存储后端（实现 `StorageService`） |
| 分布式任务调度 | 自定义队列实现（实现 `TaskQueue`） |

引擎内核已与漫剧业务解耦，所有业务逻辑通过扩展点注入，便于复用到其他 AI 生产流水线场景。
