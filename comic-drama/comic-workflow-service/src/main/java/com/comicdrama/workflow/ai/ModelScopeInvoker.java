package com.comicdrama.workflow.ai;

import com.comicdrama.common.ai.AiInvokeRequest;
import com.comicdrama.common.ai.AiInvokeResponse;
import com.comicdrama.common.ai.AiModelContext;
import com.comicdrama.common.ai.AiModelInvoker;
import com.comicdrama.common.enums.ModelCapability;
import com.comicdrama.common.enums.ModelType;
import com.comicdrama.common.storage.StorageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ModelScope 图像模型调用器（异步模式）。
 * 处理 ModelScope 平台的图像生成模型（如 Tongyi-Image-Turbo、FLUX.2-klein-9B 等）。
 * 使用 X-ModelScope-Async-Mode 提交任务，轮询 /tasks/{task_id} 获取结果。
 */
@Slf4j
@Component
public class ModelScopeInvoker implements AiModelInvoker {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final StorageService storageService;

    @Value("${ai.modelscope.timeout:600000}")
    private int timeout;

    @Value("${ai.modelscope.retry-times:2}")
    private int retryTimes;

    /** 轮询间隔（毫秒） */
    private static final long POLL_INTERVAL_MS = 3000;
    /** 最大轮询次数（@PostConstruct 中根据 timeout 配置动态计算，避免硬编码与配置不一致） */
    private int maxPollCount;
    /**
     * 当返回 SUCCEED + output_images 空数组时，额外再轮询的次数。
     * ModelScope 服务端状态更新与 OSS 文件上传不是原子操作：
     * 常先写入 task_status=SUCCEED，隔 1~3 轮才填充 output_images。
     * 如果过早 return null 就会被上层判为"空结果失败"。
     */
    private static final int SUCCEED_EMPTY_EXTRA_POLLS = 40; // 额外 2 分钟窗口（40 × 3s）

    /** 参考图转 Base64 嵌入请求体的大小阈值（字节），超过后打 WARN 日志，避免超大请求体长时间卡住 */
    private static final long BASE64_WARN_BYTES = 8 * 1024 * 1024; // 8MB

    /** 默认图像尺寸 2048x1152（16:9 比例，适配 ModelScope 宽度上限 2048） */
    private static final String DEFAULT_IMAGE_SIZE = "2048x1152";
    /** ModelScope API 允许的最大宽度/高度 */
    private static final int MAX_DIMENSION = 2048;
    /** ModelScope API 允许的最小宽度/高度 */
    private static final int MIN_DIMENSION = 64;

    /** HTTP 客户端（带 connect 超时，避免默认无超时无限卡死）。
     *  注意：由于编译环境差异，不在全局 HttpClient.Builder 上设置 requestTimeout，
     *  改为在每个 HttpRequest 上通过 HttpRequest.timeout(...) 单独设置，效果一致。 */
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    /** 图片下载/读取超时：防止本地静态服务异常或网络抖动导致线程挂死 */
    private static final Duration IMAGE_DOWNLOAD_TIMEOUT = Duration.ofSeconds(60);

    public ModelScopeInvoker(RestTemplate aiRestTemplate, ObjectMapper objectMapper,
                             StorageService storageService) {
        this.restTemplate = aiRestTemplate;
        this.objectMapper = objectMapper;
        this.storageService = storageService;
    }

    @jakarta.annotation.PostConstruct
    public void init() {
        // 根据 timeout 配置动态计算轮询次数，最少 30 次，最多 2400 次（2 小时兜底，避免超大图/长队列时过早超时）
        int calculated = (int) Math.max(30, Math.min(2400, timeout / POLL_INTERVAL_MS));
        this.maxPollCount = calculated;
        log.info("ModelScopeInvoker 初始化完成：timeout={}ms ({}min), retryTimes={}, maxPollCount={}（每次间隔{}ms, 约{}min）, succeedEmptyExtraPolls={}（额外{}s窗口）",
                timeout, timeout / 60000, retryTimes, maxPollCount, POLL_INTERVAL_MS,
                (maxPollCount * POLL_INTERVAL_MS) / 60000,
                SUCCEED_EMPTY_EXTRA_POLLS, SUCCEED_EMPTY_EXTRA_POLLS * POLL_INTERVAL_MS / 1000);
    }

    @Override
    public boolean supports(ModelType modelType) {
        return ModelType.IMAGE.equals(modelType);
    }

    @Override
    public boolean supports(String modelProvider) {
        return "modelscope".equals(modelProvider);
    }

    @Override
    public java.util.Set<String> supportedProtocols() {
        return java.util.Set.of("modelscope-image");
    }

    @Override
    public java.util.Set<ModelType> supportedModelTypes() {
        return java.util.Set.of(ModelType.IMAGE);
    }

    @Override
    public java.util.Set<ModelCapability> capabilities() {
        return java.util.Set.of(ModelCapability.IMAGE_TO_IMAGE);
    }

    @Override
    public AiInvokeResponse invoke(AiModelContext context, AiInvokeRequest request) {
        long start = System.currentTimeMillis();
        String baseUrl = context.getApiUrl();
        String submitUrl = baseUrl + "/images/generations";
        String modelName = context.resolveApiModel();

        Map<String, Object> body = buildRequestBody(context, request);
        HttpHeaders headers = buildSubmitHeaders(context);

        int maxAttempts = retryTimes + 1;
        int attempt = 0;

        while (attempt < maxAttempts) {
            attempt++;
            try {
                // 1. 提交异步任务
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
                ResponseEntity<String> response = restTemplate.postForEntity(submitUrl, entity, String.class);

                if (!response.getStatusCode().is2xxSuccessful()) {
                    int statusCode = response.getStatusCode().value();
                    String errorDetail = parseErrorBody(response.getBody());
                    log.warn("ModelScope 图像生成提交失败，attempt={}/{}, status={}, model={}, body={}",
                            attempt, maxAttempts, statusCode, modelName, errorDetail);
                    if (isNonRetryableError(statusCode)) {
                        return buildErrorResponse(statusCode, modelName, errorDetail);
                    }
                    if (attempt < maxAttempts) {
                        sleepBeforeRetry(attempt);
                    }
                    continue;
                }

                // 2. 解析 task_id
                String taskId = parseTaskId(response.getBody());
                if (!StringUtils.hasText(taskId)) {
                    log.warn("ModelScope 图像生成响应中未找到 task_id，body={}", response.getBody());
                    // 可能是同步模式返回了直接结果，尝试解析图片 URL
                    String imageUrl = parseImageUrlSync(response.getBody());
                    if (StringUtils.hasText(imageUrl)) {
                        return downloadAndBuildResponse(imageUrl, context, request, start, modelName, response.getBody());
                    }
                    if (attempt < maxAttempts) {
                        sleepBeforeRetry(attempt);
                    }
                    continue;
                }

                log.info("ModelScope 异步任务已提交，model={}, nodeKey={}, taskId={}", modelName, request.getNodeKey(), taskId);

                // 3. 轮询任务状态
                String imageUrl = pollTaskResult(baseUrl, taskId, context, modelName, request.getNodeKey());
                if (imageUrl == null) {
                    log.warn("ModelScope 异步任务失败或超时，model={}, taskId={}", modelName, taskId);
                    if (attempt < maxAttempts) {
                        sleepBeforeRetry(attempt);
                    }
                    continue;
                }

                // 4. 下载并存储图片
                return downloadAndBuildResponse(imageUrl, context, request, start, modelName, null);

            } catch (HttpStatusCodeException hsce) {
                int statusCode = hsce.getStatusCode().value();
                String errorDetail = parseErrorBody(hsce.getResponseBodyAsString());
                long costMs = System.currentTimeMillis() - start;
                log.warn("ModelScope 图像生成 HTTP 错误，attempt={}/{}, status={}, model={}, cost={}ms, detail={}",
                        attempt, maxAttempts, statusCode, modelName, costMs, errorDetail);
                if (isNonRetryableError(statusCode)) {
                    return buildErrorResponse(statusCode, modelName, errorDetail);
                }
                if (attempt >= maxAttempts) {
                    return buildErrorResponse(statusCode, modelName, errorDetail);
                }
                sleepBeforeRetry(attempt);

            } catch (Exception e) {
                long costMs = System.currentTimeMillis() - start;
                log.warn("ModelScope 图像生成网络异常，attempt={}/{}, cost={}ms, model={}, error={}",
                        attempt, maxAttempts, costMs, modelName, e.getMessage());
                if (attempt >= maxAttempts) {
                    return AiInvokeResponse.fail("图像生成调用失败（重试" + retryTimes + "次后）：" + e.getMessage());
                }
                sleepBeforeRetry(attempt);
            }
        }

        return AiInvokeResponse.fail("图像生成调用失败：未知错误");
    }

    /**
     * 轮询异步任务结果，返回图片 URL。
     * 返回 null 表示任务失败或超时。
     */
    private String pollTaskResult(String baseUrl, String taskId, AiModelContext context,
                                   String modelName, String nodeKey) {
        String pollUrl = baseUrl + "/tasks/" + taskId;
        HttpHeaders pollHeaders = buildPollHeaders(context);

        // 进入 SUCCEED + 空 output_images 后，额外再轮询的次数计数器（给 OSS 上传完成的窗口）
        int emptySucceedBudget = -1;

        for (int i = 0; i < maxPollCount; i++) {
            // 第一轮先立刻 poll，避免上来先 sleep 3s 浪费时间；之后每轮正常间隔
            if (i > 0) {
                try {
                    Thread.sleep(POLL_INTERVAL_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }

            try {
                HttpEntity<Void> entity = new HttpEntity<>(pollHeaders);
                ResponseEntity<String> response = restTemplate.exchange(pollUrl, HttpMethod.GET, entity, String.class);

                if (!response.getStatusCode().is2xxSuccessful()) {
                    log.warn("ModelScope 轮询任务状态失败，taskId={}, status={}, poll={}/{}", taskId, response.getStatusCode(), i + 1, maxPollCount);
                    continue;
                }

                JsonNode root = objectMapper.readTree(response.getBody());
                String taskStatus = root.path("task_status").asText("");

                if ("SUCCEED".equals(taskStatus)) {
                    JsonNode images = root.path("output_images");
                    if (images.isArray() && !images.isEmpty()) {
                        String imageUrl = images.get(0).asText();
                        log.info("ModelScope 异步任务成功，model={}, nodeKey={}, taskId={}, pollCount={}/{}, imageUrl={}",
                                modelName, nodeKey, taskId, i + 1, maxPollCount, imageUrl);
                        return imageUrl;
                    }
                    // SUCCEED + 空 output_images：
                    // 大概率是 ModelScope 服务端先写 SUCCEED，而 OSS 文件链接还没写入 output_images。
                    // 给 2 分钟宽限期多等 N 轮，仍空才真正判空。
                    if (emptySucceedBudget < 0) {
                        emptySucceedBudget = SUCCEED_EMPTY_EXTRA_POLLS;
                        log.info("ModelScope 任务状态 SUCCEED 但 output_images 为空，进入宽限期（额外{}轮, {}s），taskId={}, poll={}",
                                SUCCEED_EMPTY_EXTRA_POLLS, SUCCEED_EMPTY_EXTRA_POLLS * POLL_INTERVAL_MS / 1000, taskId, i + 1);
                    }
                    if (emptySucceedBudget-- > 0) {
                        // 继续下一轮轮询
                        if ((i + 1) % 10 == 0) {
                            log.info("ModelScope 任务处理中（SUCCEED-空结果宽限）... model={}, taskId={}, poll={}/{}, 剩余宽限={}轮",
                                    modelName, taskId, i + 1, maxPollCount, emptySucceedBudget);
                        }
                        continue;
                    }
                    log.warn("ModelScope 任务标记 SUCCEED，但经过{}轮宽限后 output_images 仍为空，放弃，taskId={}",
                            SUCCEED_EMPTY_EXTRA_POLLS, taskId);
                    return null;
                } else if ("FAILED".equals(taskStatus)) {
                    String errors = root.path("errors").asText("无详细错误信息");
                    log.warn("ModelScope 异步任务失败，model={}, taskId={}, errors={}", modelName, taskId, errors);
                    return null;
                }
                // 非终态：只要任务还在 PENDING/RUNNING，一旦之前进入了 SUCCEED 宽限就重置（说明状态可能回滚）
                emptySucceedBudget = -1;
                // PENDING / RUNNING 继续轮询（每10次打印一次进度，避免刷爆日志）
                if ((i + 1) % 10 == 0) {
                    log.info("ModelScope 任务处理中... model={}, taskId={}, poll={}/{}, status={}",
                            modelName, taskId, i + 1, maxPollCount, taskStatus);
                }
            } catch (Exception e) {
                log.warn("ModelScope 轮询异常，taskId={}, poll={}/{}, error={}", taskId, i + 1, maxPollCount, e.getMessage());
            }
        }

        log.warn("ModelScope 异步任务轮询超时，model={}, taskId={}, maxPoll={}（配置timeout={}ms, 约{}min）",
                modelName, taskId, maxPollCount, timeout, timeout / 60000);
        return null;
    }

    /**
     * 下载图片并构建成功响应。
     */
    private AiInvokeResponse downloadAndBuildResponse(String imageUrl, AiModelContext context,
                                                      AiInvokeRequest request, long start,
                                                      String modelName, String rawResponse) {
        try {
            String storedUrl = downloadAndStore(imageUrl, context, request);
            long costMs = System.currentTimeMillis() - start;
            log.info("ModelScope 图像生成调用成功，model={}, nodeKey={}, cost={}ms, imageUrl={}",
                    modelName, request.getNodeKey(), costMs, storedUrl);
            return AiInvokeResponse.builder()
                    .success(true)
                    .resourceUrl(storedUrl)
                    .resourceType("image")
                    .costMs(costMs)
                    .rawResponse(rawResponse)
                    .build();
        } catch (Exception e) {
            long costMs = System.currentTimeMillis() - start;
            log.error("ModelScope 图片下载/存储失败，model={}, imageUrl={}, error={}", modelName, imageUrl, e.getMessage());
            return AiInvokeResponse.fail("图片下载失败：" + e.getMessage());
        }
    }

    private boolean isNonRetryableError(int statusCode) {
        return statusCode == 400 || statusCode == 401 || statusCode == 403 || statusCode == 429;
    }

    private AiInvokeResponse buildErrorResponse(int statusCode, String modelName, String errorDetail) {
        String userMessage;
        switch (statusCode) {
            case 429:
                userMessage = String.format("图像模型「%s」今日配额已耗尽（HTTP 429）。请等待明日配额重置或在系统设置中切换到其他模型。", modelName);
                break;
            case 400:
                userMessage = String.format("图像模型「%s」请求参数错误（HTTP 400）：%s", modelName, errorDetail);
                break;
            case 401:
                userMessage = String.format("图像模型「%s」API 密钥无效（HTTP 401），请检查 API Key 配置", modelName);
                break;
            default:
                userMessage = String.format("图像模型「%s」调用失败（HTTP %d）：%s", modelName, statusCode, errorDetail);
                break;
        }
        return AiInvokeResponse.fail(userMessage);
    }

    private String parseErrorBody(String errorBody) {
        if (errorBody == null || errorBody.isEmpty()) return "无详细错误信息";
        try {
            JsonNode root = objectMapper.readTree(errorBody);
            JsonNode errorNode = root.path("error");
            if (!errorNode.isMissingNode()) {
                String msg = errorNode.path("message").asText(null);
                if (msg != null && !msg.isEmpty()) return msg;
            }
            String message = root.path("message").asText(null);
            if (message != null && !message.isEmpty()) return message;
        } catch (Exception e) {
            log.debug("解析错误响应体失败: {}", errorBody);
        }
        return errorBody.length() > 200 ? errorBody.substring(0, 200) + "..." : errorBody;
    }

    private void sleepBeforeRetry(int attempt) {
        try {
            Thread.sleep(1000L * attempt);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private Map<String, Object> buildRequestBody(AiModelContext context, AiInvokeRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", context.resolveApiModel());
        body.put("prompt", request.getPrompt());

        Map<String, Object> extra = request.getExtra();
        String imageSize;
        if (extra != null && extra.containsKey("size")) {
            imageSize = clampSize((String) extra.get("size"));
        } else {
            imageSize = DEFAULT_IMAGE_SIZE;
        }
        body.put("size", imageSize);
        body.put("n", 1);

        if (StringUtils.hasText(request.getReferenceImageUrl())) {
            // 关键修复：referenceImageUrl 通常是本地地址（http://127.0.0.1:8xxx/static/...），
            // 云端模型服务端无法回源下载（connect refused）。
            // 策略：本地先下载图片，转 Base64 Data URL（data:image/png;base64,xxxxx）嵌入请求体，
            // 同时兼容 image_url 数组 和 reference_image 单字符串两种字段。
            String imagePayload = resolveReferenceImageForModelScope(request.getReferenceImageUrl(),
                    context.resolveApiModel(), request.getNodeKey());
            body.put("image_url", java.util.List.of(imagePayload));
            body.put("reference_image", imagePayload);
            if (extra != null && extra.containsKey("strength")) {
                body.put("strength", extra.get("strength"));
            } else {
                body.put("strength", 0.6);
            }
        }

        return body;
    }

    /**
     * 将参考图 URL 转换为 ModelScope 可直接消费的载荷。
     * 优先尝试将本地/内网地址转成 Base64 Data URL 嵌入，避免云端回源下载失败。
     * 若下载失败，降级返回原始 URL。
     */
    private String resolveReferenceImageForModelScope(String referenceImageUrl, String modelName, String nodeKey) {
        try {
            long startDownload = System.currentTimeMillis();
            byte[] bytes = downloadImageAsBytes(referenceImageUrl);
            long downloadMs = System.currentTimeMillis() - startDownload;

            if (bytes.length > BASE64_WARN_BYTES) {
                log.warn("ModelScope 参考图尺寸过大（{}KB > 8MB阈值），转Base64后请求体将非常大，" +
                                "可能导致上传慢/超时。建议降低生成图片的分辨率或使用JPEG格式。" +
                                "model={}, nodeKey={}, srcUrl={}, sizeKB={}, downloadCost={}ms",
                        bytes.length / 1024, modelName, nodeKey, maskUrl(referenceImageUrl),
                        bytes.length / 1024, downloadMs);
            }

            String mimeType = detectMimeType(referenceImageUrl, bytes);
            String base64 = Base64.getEncoder().encodeToString(bytes);
            String dataUrl = "data:" + mimeType + ";base64," + base64;
            log.info("ModelScope 参考图已转 Base64 嵌入（避免云端回源下载），model={}, nodeKey={}, srcUrl={}, " +
                            "sizeKB={}, mime={}, downloadCost={}ms, encodeCost={}ms",
                    modelName, nodeKey, maskUrl(referenceImageUrl), bytes.length / 1024, mimeType,
                    downloadMs, (System.currentTimeMillis() - startDownload - downloadMs));
            return dataUrl;
        } catch (Exception e) {
            // 关键：不要吞掉超时类异常，超时说明网络/服务有问题，降级传原始URL大概率也会失败，
            // 但这里仍然降级，同时把异常类型和耗时打印出来，方便定位"卡住"时到底在哪一步。
            log.warn("ModelScope 参考图转 Base64 失败（{}），降级回传原始 URL（可能导致云端无法下载）。" +
                            "model={}, nodeKey={}, error={}",
                    e.getClass().getSimpleName(), modelName, nodeKey, e.getMessage());
            return referenceImageUrl;
        }
    }

    /** 下载图片为字节数组 */
    private byte[] downloadImageAsBytes(String imageUrl) throws Exception {
        try (InputStream is = downloadImage(imageUrl);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) {
                baos.write(buf, 0, n);
            }
            return baos.toByteArray();
        }
    }

    /** 根据 URL 扩展名或魔数推断 MIME 类型（默认为 image/png） */
    private String detectMimeType(String url, byte[] headerBytes) {
        try {
            String lower = url == null ? "" : url.toLowerCase();
            int qIdx = lower.indexOf('?');
            String path = qIdx >= 0 ? lower.substring(0, qIdx) : lower;
            if (path.endsWith(".jpg") || path.endsWith(".jpeg")) return "image/jpeg";
            if (path.endsWith(".webp")) return "image/webp";
            if (path.endsWith(".gif")) return "image/gif";
            if (path.endsWith(".bmp")) return "image/bmp";
            // 通过魔数兜底判断
            if (headerBytes != null && headerBytes.length >= 4) {
                // JPEG: FF D8 FF
                if ((headerBytes[0] & 0xFF) == 0xFF && (headerBytes[1] & 0xFF) == 0xD8 && (headerBytes[2] & 0xFF) == 0xFF) {
                    return "image/jpeg";
                }
                // PNG: 89 50 4E 47
                if ((headerBytes[0] & 0xFF) == 0x89 && "PNG".equals(new String(headerBytes, 1, 3, java.nio.charset.StandardCharsets.ISO_8859_1))) {
                    return "image/png";
                }
                // WebP: RIFF ... WEBP
                if ("RIFF".equals(new String(headerBytes, 0, 4, java.nio.charset.StandardCharsets.ISO_8859_1))
                        && headerBytes.length >= 12
                        && "WEBP".equals(new String(headerBytes, 8, 4, java.nio.charset.StandardCharsets.ISO_8859_1))) {
                    return "image/webp";
                }
                // GIF: GIF8
                if ("GIF8".equals(new String(headerBytes, 0, 4, java.nio.charset.StandardCharsets.ISO_8859_1))) {
                    return "image/gif";
                }
            }
        } catch (Exception ignore) {
            // fall through to default
        }
        return "image/png";
    }

    /** 对 URL 做脱敏日志输出 */
    private String maskUrl(String url) {
        if (url == null || url.length() <= 40) return url;
        return url.substring(0, 24) + "..." + url.substring(url.length() - 16);
    }

    /**
     * 将尺寸限制在 ModelScope API 允许的范围内 [64, 2048]。
     * 如果原始尺寸超出限制，按比例缩小到最大维度以内，保持宽高比。
     */
    private String clampSize(String size) {
        if (size == null || !size.contains("x")) {
            return DEFAULT_IMAGE_SIZE;
        }
        try {
            String[] parts = size.split("x");
            int width = Integer.parseInt(parts[0]);
            int height = Integer.parseInt(parts[1]);

            if (width > MAX_DIMENSION || height > MAX_DIMENSION) {
                double scale = (double) MAX_DIMENSION / Math.max(width, height);
                width = (int) Math.round(width * scale);
                height = (int) Math.round(height * scale);
                width = Math.max(width, MIN_DIMENSION);
                height = Math.max(height, MIN_DIMENSION);
                log.info("ModelScope 图片尺寸超出限制，已按比例缩小: {} -> {}x{}", size, width, height);
            }
            return width + "x" + height;
        } catch (Exception e) {
            log.warn("解析图片尺寸失败，使用默认值: {}", size);
            return DEFAULT_IMAGE_SIZE;
        }
    }

    /**
     * 提交任务的请求头（包含异步模式标识）。
     */
    private HttpHeaders buildSubmitHeaders(AiModelContext context) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + context.getApiKey());
        headers.set("X-ModelScope-Async-Mode", "true");
        return headers;
    }

    /**
     * 轮询任务结果的请求头。
     */
    private HttpHeaders buildPollHeaders(AiModelContext context) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + context.getApiKey());
        headers.set("X-ModelScope-Task-Type", "image_generation");
        return headers;
    }

    /**
     * 从异步任务提交响应中解析 task_id。
     */
    private String parseTaskId(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            return root.path("task_id").asText(null);
        } catch (Exception e) {
            log.warn("解析 ModelScope task_id 失败: {}", responseBody, e);
        }
        return null;
    }

    /**
     * 兼容同步模式的图片 URL 解析（当 API 未返回 task_id 时使用）。
     */
    private String parseImageUrlSync(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode data = root.path("data");
            if (data.isArray() && !data.isEmpty()) {
                return data.get(0).path("url").asText();
            }
            // 某些模型直接返回 images 字段
            JsonNode images = root.path("images");
            if (images.isArray() && !images.isEmpty()) {
                return images.get(0).asText();
            }
            // output_images 字段
            JsonNode outputImages = root.path("output_images");
            if (outputImages.isArray() && !outputImages.isEmpty()) {
                return outputImages.get(0).asText();
            }
        } catch (Exception e) {
            log.warn("解析 ModelScope 同步响应体失败: {}", responseBody, e);
        }
        return null;
    }

    private String downloadAndStore(String imageUrl, AiModelContext context, AiInvokeRequest request) throws Exception {
        String objectKey = request.getTaskId() + "/images/" + UUID.randomUUID() + ".png";
        try (InputStream is = downloadImage(imageUrl)) {
            String storedKey = storageService.upload(is, objectKey, -1, "image/png");
            return storageService.signUrl(storedKey, 3600);
        }
    }

    private InputStream downloadImage(String imageUrl) throws Exception {
        java.net.http.HttpRequest httpRequest = java.net.http.HttpRequest.newBuilder()
                .uri(URI.create(imageUrl))
                .GET()
                .timeout(IMAGE_DOWNLOAD_TIMEOUT) // 请求级超时（60秒）：防止本地静态服务异常导致线程挂死
                .build();
        HttpResponse<java.io.InputStream> response = httpClient.send(httpRequest,
                java.net.http.HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw new RuntimeException("下载图片失败，状态码: " + response.statusCode() + ", url=" + maskUrl(imageUrl));
        }
        return response.body();
    }
}
