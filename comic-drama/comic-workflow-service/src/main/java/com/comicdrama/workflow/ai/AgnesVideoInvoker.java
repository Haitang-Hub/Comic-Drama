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
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Agnes Video V2.0 视频模型调用器。
 * 支持文生视频 / 图生视频 / 多关键帧动画（keyframes 模式）。
 * 视频生成是异步流程，采用「提交任务 → 轮询状态 → 下载存储」三段式流程。
 *
 * <p>注意（与脚本 gen_video.py 对齐的关键约束）：
 * <ul>
 *   <li>extra_body.image 数组必须传 {@code data:image/png;base64,...} 形式的 base64 data URI，
 *       <b>外部 HTTP URL 会被 API 直接拒绝</b>，本类内部自动下载 URL 并转 Base64</li>
 *   <li>negative_prompt 为顶层必传字段，缺失时 API 参数校验可能失败（兜底传空串）</li>
 *   <li>num_frames 必须满足 n*8+1 规则，否则提交被拒</li>
 * </ul>
 */
@Slf4j
@Component
public class AgnesVideoInvoker implements AiModelInvoker {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final StorageService storageService;

    @Value("${ai.agnes.timeout:120000}")
    private int timeout;

    @Value("${ai.agnes.retry-times:3}")
    private int retryTimes;

    @Value("${ai.agnes.poll-interval:3000}")
    private long pollInterval;

    @Value("${ai.agnes.poll-max-attempts:120}")
    private int pollMaxAttempts;

    /** 队列满 503 最大重试次数（对齐 gen_video.py MAX_RETRY_QUEUE=8） */
    @Value("${ai.agnes.queue-max-retry:8}")
    private int queueMaxRetry;

    /** 队列满等待秒数（对齐 gen_video.py RETRY_WAIT=45） */
    @Value("${ai.agnes.queue-retry-wait-seconds:45}")
    private int queueRetryWaitSeconds;

    public AgnesVideoInvoker(RestTemplate aiRestTemplate, ObjectMapper objectMapper, StorageService storageService) {
        this.restTemplate = aiRestTemplate;
        this.objectMapper = objectMapper;
        this.storageService = storageService;
    }

    // ==================== 路由接口 ====================

    @Override
    public boolean supports(ModelType modelType) {
        return ModelType.VIDEO.equals(modelType);
    }

    @Override
    public boolean supports(String modelProvider) {
        return "agnes_video".equals(modelProvider) || "agnes".equals(modelProvider);
    }

    @Override
    public java.util.Set<String> supportedProtocols() {
        return java.util.Set.of("agnes-video");
    }

    @Override
    public java.util.Set<ModelType> supportedModelTypes() {
        return java.util.Set.of(ModelType.VIDEO);
    }

    @Override
    public java.util.Set<ModelCapability> capabilities() {
        return java.util.Set.of(ModelCapability.FIRST_FRAME_LOCK);
    }

    // ==================== 调用入口 ====================

    @Override
    public AiInvokeResponse invoke(AiModelContext context, AiInvokeRequest request) {
        long globalStart = System.currentTimeMillis();

        // 1. 提交任务（含 503 队列满重试 + 图片 URL 自动转 Base64）
        SubmitResult submitResult = submitTaskWithQueueRetry(context, request);
        if (!submitResult.success || !StringUtils.hasText(submitResult.videoId)) {
            String reason = submitResult.errorDetail != null ? submitResult.errorDetail : "未获取到 video_id";
            log.error("[AgnesVideo] 任务提交最终失败：{}（nodeKey={}）", reason, request.getNodeKey());
            return AiInvokeResponse.fail("Agnes 视频任务提交失败：" + reason);
        }
        log.info("[AgnesVideo] 任务提交成功，taskId={}, nodeKey={}, model={}",
                submitResult.videoId, request.getNodeKey(), context.resolveApiModel());

        // 2. 轮询任务状态 → 完成后下载
        return pollForResult(context, request, submitResult.videoId, globalStart);
    }

    // ==================== 任务提交（含队列满重试） ====================

    private static class SubmitResult {
        final boolean success;
        final String videoId;
        final String errorDetail;

        SubmitResult(boolean success, String videoId, String errorDetail) {
            this.success = success;
            this.videoId = videoId;
            this.errorDetail = errorDetail;
        }

        static SubmitResult ok(String id) { return new SubmitResult(true, id, null); }
        static SubmitResult fail(String detail) { return new SubmitResult(false, null, detail); }
    }

    private SubmitResult submitTaskWithQueueRetry(AiModelContext context, AiInvokeRequest request) {
        // 关键：提交前先构造请求体（含 URL→Base64 转换，失败直接返回）
        Map<String, Object> body;
        try {
            body = buildRequestBody(context, request);
        } catch (Exception e) {
            return SubmitResult.fail("构造请求体失败：" + e.getMessage());
        }
        HttpHeaders headers = buildHeaders(context);

        // 提交前打印摘要（图片 Base64 非常长，禁止打印完整 body，只打印摘要）
        logSummarizeBody(context, request, body);

        String submitUrl = context.getApiUrl() + "/v1/videos";
        int queueRetryCount = 0;
        int normalRetryCount = 0;
        int maxNormalAttempts = retryTimes + 1;
        String lastError = "未知错误";

        while (true) {
            try {
                log.info("[AgnesVideo] 提交任务：url={}, queueRetry={}/{}, normalRetry={}/{}",
                        submitUrl, queueRetryCount, queueMaxRetry, normalRetryCount, retryTimes);

                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
                ResponseEntity<String> response = restTemplate.postForEntity(submitUrl, entity, String.class);
                int statusCode = response.getStatusCode().value();

                // 队列满 503 重试（特殊分支，对齐 gen_video.py）
                if (statusCode == 503) {
                    queueRetryCount++;
                    lastError = handle503(response.getBody(), queueRetryCount);
                    if (lastError == null) {
                        continue; // 已 sleep，下一轮重试
                    }
                    return SubmitResult.fail(lastError); // 已达最大次数
                }

                // 普通失败（非 2xx）
                if (!response.getStatusCode().is2xxSuccessful()) {
                    normalRetryCount++;
                    String errorDetail = parseErrorBody(response.getBody());
                    lastError = String.format("HTTP %d：%s", statusCode, errorDetail);
                    log.warn("[AgnesVideo] 提交返回非成功，status={}, detail={}, attempt={}/{}",
                            statusCode, errorDetail, normalRetryCount, retryTimes);
                    // 非200也把响应体打出来方便排错
                    log.debug("[AgnesVideo] 提交失败响应体：{}", truncate(response.getBody(), 4000));

                    if (isNonRetryableError(statusCode)) {
                        return SubmitResult.fail(lastError);
                    }
                    if (normalRetryCount >= maxNormalAttempts) {
                        return SubmitResult.fail(lastError);
                    }
                    sleepBeforeRetry(normalRetryCount);
                    continue;
                }

                // 成功 2xx：提取 video_id
                String rawBody = response.getBody();
                log.debug("[AgnesVideo] 提交成功响应体（前3000字符）：{}", truncate(rawBody, 3000));
                try {
                    JsonNode root = objectMapper.readTree(rawBody);
                    String videoId = extractVideoId(root);
                    if (StringUtils.hasText(videoId)) {
                        return SubmitResult.ok(videoId);
                    }
                    // 响应成功但没有 video_id → 打印完整响应以便排查
                    lastError = "响应未包含 video_id 字段，响应=" + truncate(rawBody, 2000);
                    log.error("[AgnesVideo] 提交 HTTP 200 但未取到 video_id：body={}", truncate(rawBody, 4000));
                } catch (Exception parseEx) {
                    lastError = "解析响应 JSON 失败：" + parseEx.getMessage() + "，body=" + truncate(rawBody, 1000);
                    log.error("[AgnesVideo] " + lastError);
                }

                // 没取到 ID 也按失败重试（可能是偶发异常响应结构）
                normalRetryCount++;
                if (normalRetryCount >= maxNormalAttempts) {
                    return SubmitResult.fail(lastError);
                }
                sleepBeforeRetry(normalRetryCount);

            } catch (HttpStatusCodeException hsce) {
                int statusCode = hsce.getStatusCode().value();
                if (statusCode == 503) {
                    queueRetryCount++;
                    lastError = handle503(hsce.getResponseBodyAsString(), queueRetryCount);
                    if (lastError == null) continue;
                    return SubmitResult.fail(lastError);
                }

                normalRetryCount++;
                String errorDetail = parseErrorBody(hsce.getResponseBodyAsString());
                lastError = String.format("HTTP %d：%s", statusCode, errorDetail);
                log.warn("[AgnesVideo] 提交任务 HTTP 异常：status={}, detail={}, attempt={}/{}",
                        statusCode, errorDetail, normalRetryCount, retryTimes);

                if (isNonRetryableError(statusCode) || normalRetryCount >= maxNormalAttempts) {
                    return SubmitResult.fail(lastError);
                }
                sleepBeforeRetry(normalRetryCount);

            } catch (Exception e) {
                normalRetryCount++;
                lastError = "网络异常：" + e.getMessage();
                log.warn("[AgnesVideo] 提交任务异常：{}，attempt={}/{}", e.getMessage(), normalRetryCount, retryTimes);
                if (normalRetryCount >= maxNormalAttempts) {
                    return SubmitResult.fail(lastError);
                }
                sleepBeforeRetry(normalRetryCount);
            }
        }
    }

    /** 处理 503：返回 null 表示已 sleep 可继续重试，返回 String 表示已达上限并给出错误 */
    private String handle503(String body, int queueRetryCount) {
        if (queueRetryCount > queueMaxRetry) {
            return String.format("任务队列长期爆满，已达最大重试次数 queueMaxRetry=%d", queueMaxRetry);
        }
        String code = "";
        try {
            JsonNode respJson = objectMapper.readTree(body);
            code = respJson.path("code").asText("");
        } catch (Exception ignored) { /* ignore */ }
        if ("video_queue_full".equals(code)) {
            log.warn("[AgnesVideo] 队列已满(code=video_queue_full)，等待{}秒后重试 {}/{}",
                    queueRetryWaitSeconds, queueRetryCount, queueMaxRetry);
        } else {
            log.warn("[AgnesVideo] 503（code={}/未知），等待{}秒后重试 {}/{}",
                    code, queueRetryWaitSeconds, queueRetryCount, queueMaxRetry);
        }
        try {
            Thread.sleep(queueRetryWaitSeconds * 1000L);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return "队列重试等待被中断";
        }
        return null;
    }

    /** 从提交响应 JSON 中提取 video_id（支持多种字段结构） */
    private String extractVideoId(JsonNode root) {
        if (root == null) return null;
        String id = root.path("video_id").asText(null);
        if (!StringUtils.hasText(id)) id = root.path("id").asText(null);
        if (!StringUtils.hasText(id)) {
            // 兼容：{data: {video_id: ...}}
            JsonNode data = root.path("data");
            if (!data.isMissingNode()) {
                id = data.path("video_id").asText(null);
                if (!StringUtils.hasText(id)) id = data.path("id").asText(null);
            }
        }
        return id;
    }

    /** 打印请求体摘要（避免泄露 Base64 大图内容） */
    private void logSummarizeBody(AiModelContext ctx, AiInvokeRequest req, Map<String, Object> body) {
        StringBuilder sb = new StringBuilder();
        sb.append("[AgnesVideo] 提交请求体摘要：model=").append(body.get("model"));
        sb.append(", size=").append(body.get("width")).append("x").append(body.get("height"));
        sb.append(", fps=").append(body.get("frame_rate"));
        sb.append(", frames=").append(body.get("num_frames"));
        Object extraBody = body.get("extra_body");
        if (extraBody instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> eb = (Map<String, Object>) extraBody;
            Object imgs = eb.get("image");
            if (imgs instanceof List) {
                int n = ((List<?>) imgs).size();
                sb.append(", images=").append(n);
                // 打印每张的 data:image/xxx;base64 前缀长度（确认是 Base64 data URI）
                for (int i = 0; i < n; i++) {
                    Object it = ((List<?>) imgs).get(i);
                    if (it instanceof String s) {
                        int colon = s.indexOf(";");
                        String head = colon > 0 ? s.substring(0, colon) : s.substring(0, Math.min(20, s.length()));
                        sb.append(" [").append(i).append("]=").append(head).append(",len=").append(s.length());
                    }
                }
            }
            if (eb.containsKey("prompt_list")) {
                int pl = eb.get("prompt_list") instanceof List ? ((List<?>) eb.get("prompt_list")).size() : 0;
                sb.append(", prompt_list=").append(pl);
            }
            sb.append(", mode=").append(eb.getOrDefault("mode", "-"));
        } else {
            sb.append(", 纯文生（无参考图）");
        }
        String neg = (String) body.get("negative_prompt");
        sb.append(", neg_len=").append(neg == null ? 0 : neg.length());
        String prompt = (String) body.get("prompt");
        sb.append(", prompt_len=").append(prompt == null ? 0 : prompt.length());
        if (body.containsKey("seed")) sb.append(", seed=").append(body.get("seed"));
        log.info(sb.toString());
    }

    // ==================== 轮询 + 下载 ====================

    private AiInvokeResponse pollForResult(AiModelContext context, AiInvokeRequest request,
                                           String taskVideoId, long globalStart) {
        String queryBaseUrl = resolveQueryUrl(context, request);
        String modelName = context.resolveApiModel();
        HttpHeaders headers = buildHeaders(context);
        int pollAttempts = 0;

        log.info("[AgnesVideo] 轮询地址：{}?video_id={}&model_name={}", queryBaseUrl, taskVideoId, modelName);

        while (pollAttempts < pollMaxAttempts) {
            pollAttempts++;
            try {
                Thread.sleep(pollInterval);

                String queryUrl = queryBaseUrl + "?video_id=" + taskVideoId + "&model_name=" + modelName;
                HttpEntity<Void> entity = new HttpEntity<>(headers);
                ResponseEntity<String> response = restTemplate.exchange(queryUrl,
                        org.springframework.http.HttpMethod.GET, entity, String.class);

                if (response.getStatusCode().is2xxSuccessful()) {
                    JsonNode root = objectMapper.readTree(response.getBody());
                    String status = root.path("status").asText("");
                    int progress = root.path("progress").asInt(0);

                    if (pollAttempts % 5 == 0 || "completed".equals(status) || "failed".equals(status) || "error".equals(status)) {
                        log.info("[AgnesVideo] 轮询状态：taskId={}, status={}, progress={}%, poll={}/{}",
                                taskVideoId, status, progress, pollAttempts, pollMaxAttempts);
                    }

                    switch (status) {
                        case "completed":
                            String videoDownloadUrl = root.path("url").asText(null);
                            long costMs = System.currentTimeMillis() - globalStart;
                            if (!StringUtils.hasText(videoDownloadUrl)) {
                                log.error("[AgnesVideo] completed 但无下载 url，完整响应：{}", truncate(response.getBody(), 3000));
                                return AiInvokeResponse.fail("视频生成完成但未返回下载 url");
                            }
                            try {
                                String storedUrl = downloadAndStoreVideo(videoDownloadUrl, request);
                                Map<String, Object> extra = new HashMap<>();
                                extra.put("progress", 100);
                                extra.put("taskVideoId", taskVideoId);
                                extra.put("width", root.path("width").asInt(0));
                                extra.put("height", root.path("height").asInt(0));
                                extra.put("duration", root.path("duration").asInt(0));

                                log.info("[AgnesVideo] 视频生成成功，taskId={}, cost={}ms, url={}",
                                        taskVideoId, costMs, storedUrl);

                                return AiInvokeResponse.builder()
                                        .success(true)
                                        .resourceUrl(storedUrl)
                                        .resourceType("video")
                                        .costMs(costMs)
                                        .rawResponse(truncate(response.getBody(), 2000))
                                        .extra(extra)
                                        .build();
                            } catch (Exception downloadEx) {
                                log.error("[AgnesVideo] 视频下载/存储失败：{}", downloadEx.getMessage());
                                return AiInvokeResponse.fail("视频下载存储失败：" + downloadEx.getMessage());
                            }

                        case "failed":
                        case "error":
                            String errMsg = root.path("message").asText("视频生成失败（无错误详情）");
                            log.error("[AgnesVideo] 任务失败，taskId={}, error={}, raw={}",
                                    taskVideoId, errMsg, truncate(response.getBody(), 2000));
                            return AiInvokeResponse.fail(errMsg);

                        case "processing":
                        case "queued":
                        case "pending":
                        default:
                            break;
                    }
                } else {
                    log.warn("[AgnesVideo] 轮询接口异常 HTTP {}：taskId={}，poll={}/{}，body={}",
                            response.getStatusCode().value(), taskVideoId, pollAttempts, pollMaxAttempts,
                            truncate(response.getBody(), 500));
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return AiInvokeResponse.fail("Agnes 视频轮询被中断");
            } catch (Exception e) {
                if (pollAttempts % 10 == 0) {
                    log.warn("[AgnesVideo] 轮询异常（每10次打印一次）：taskId={}, poll={}/{}, error={}",
                            taskVideoId, pollAttempts, pollMaxAttempts, e.getMessage());
                }
            }
        }

        log.error("[AgnesVideo] 轮询超时，taskId={}, maxAttempts={}", taskVideoId, pollMaxAttempts);
        return AiInvokeResponse.fail("视频生成轮询超时（超过 " + pollMaxAttempts + " 次尝试）");
    }

    // ==================== 请求体 / 请求头构建（关键：URL→Base64） ====================

    private Map<String, Object> buildRequestBody(AiModelContext context, AiInvokeRequest request) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("model", context.resolveApiModel());
        body.put("prompt", request.getPrompt() != null ? request.getPrompt() : "");

        Map<String, Object> extra = request.getExtra();

        // 尺寸 / 帧率 / 帧数 / 时长
        int width = extra != null && extra.containsKey("width")
                ? ((Number) extra.get("width")).intValue() : 832;
        int height = extra != null && extra.containsKey("height")
                ? ((Number) extra.get("height")).intValue() : 1088;
        int fps = extra != null && extra.containsKey("fps")
                ? ((Number) extra.get("fps")).intValue() : 24;
        int duration = extra != null && extra.containsKey("duration")
                ? ((Number) extra.get("duration")).intValue() : 5;
        int totalFrames = calcTotalFrames(duration, fps);

        body.put("width", width);
        body.put("height", height);
        body.put("frame_rate", fps);
        body.put("num_frames", totalFrames);

        // 【必填】负面提示词（脚本固定传 NEG_PROMPT，此处兜底传空串避免缺失）
        String negPrompt = "";
        if (extra != null && extra.containsKey("negative_prompt") && extra.get("negative_prompt") != null) {
            negPrompt = String.valueOf(extra.get("negative_prompt"));
        }
        body.put("negative_prompt", negPrompt);

        // 固定种子（可选，-1 或不填表示随机）
        if (extra != null && extra.containsKey("seed")) {
            Number seed = (Number) extra.get("seed");
            if (seed != null && seed.intValue() >= 0) {
                body.put("seed", seed.intValue());
            }
        }

        // extra_body：图生视频 / 关键帧模式（Agnes image 字段只认 base64 data URI，需要提前转换）
        Map<String, Object> extraBody = new HashMap<>();
        boolean hasImageInput = false;

        // Agnes API 关于 mode 字段的硬性规则（错误信息已明确）：
        //   「mode must be omitted for text-to-video or single-image video,
        //     or set to keyframes for keyframe video.」
        //   即：
        //   纯文生（T2V）    → 完全不传 extra_body（自然也没有 mode、image 字段）
        //   单图首帧锁定     → 传 extra_body.image（数组形式，1 个元素），mode 必须完全省略
        //   多关键帧动画     → 传 extra_body.image（2~3 元素数组） + mode=keyframes
        //   N>3（兜底）      → 截取前 3 张，仍走 keyframes 模式（调用层应提前抽样，这里防御）

        // 方式 A：多图关键帧（extra.image_list 是 URL 或 Base64 字符串集合）
        if (extra != null && extra.containsKey("image_list")) {
            Object imgListObj = extra.get("image_list");
            if (imgListObj instanceof java.util.Collection<?> col && !col.isEmpty()) {
                List<String> b64List = convertImageListToBase64(col);
                int size = b64List.size();
                if (size == 1) {
                    // 1 张：退化为「单图首帧锁定」→ image 数组[1]，mode 必须省略
                    extraBody.put("image", b64List); // 数组形式（1个元素），与脚本对齐
                    // —— 注意：此处绝对不写 extraBody.put("mode", ...)！
                    hasImageInput = true;
                    log.warn("[AgnesVideo] image_list 实际可用{}张，不足keyframes要求(2~3)，退化为单图首帧锁定（省略mode，image为单元素数组）", size);
                } else if (size >= 2 && size <= 3) {
                    // 2~3 张：标准 keyframes 模式 → image 数组 + mode=keyframes
                    extraBody.put("image", b64List);
                    extraBody.put("mode", "keyframes");
                    hasImageInput = true;
                } else if (size > 3) {
                    // >3 张：防御性截取前 3 张（调用层应提前抽样，这里兜底避免直接 400）
                    log.warn("[AgnesVideo] image_list 可用{}张超过 keyframes 上限(3)，截取前3张避免 API 400", size);
                    List<String> trimmed = new ArrayList<>(b64List.subList(0, 3));
                    extraBody.put("image", trimmed);
                    extraBody.put("mode", "keyframes");
                    hasImageInput = true;
                }
                // N=0 不写入
            }
        }

        // 方式 B：单张首帧（首帧锁定），优先级低于多图
        // —— 单图：extra_body.image 必须是数组形式（1个元素），并且必须完全省略 mode 字段
        if (!hasImageInput && StringUtils.hasText(request.getReferenceImageUrl())) {
            String b64 = ensureBase64DataUri(request.getReferenceImageUrl());
            if (b64 != null) {
                extraBody.put("image", List.of(b64)); // 数组形式（1个元素），对齐脚本
                // —— 严格按 API 约束：单图不能写 mode！
                hasImageInput = true;
                log.info("[AgnesVideo] 单张首帧锁定：extra_body.image = [单元素数组，len={}]，省略mode（API 要求 T2V/单图必须省略mode）",
                        b64.length());
            } else {
                log.warn("[AgnesVideo] 首帧 URL 下载转 Base64 失败，将退化为纯文生视频：url={}",
                        truncate(request.getReferenceImageUrl(), 120));
            }
        }

        // 逐帧提示词（可选）：仅 mode=keyframes 时才写入；非 keyframes 模式写了可能触发校验
        if (extra != null && extra.containsKey("prompt_list")) {
            Object promptListObj = extra.get("prompt_list");
            if (promptListObj instanceof java.util.Collection<?> col && !col.isEmpty()) {
                List<String> pList = new ArrayList<>();
                for (Object o : col) if (o != null) pList.add(String.valueOf(o));
                boolean isKeyframesMode = hasImageInput && "keyframes".equals(extraBody.get("mode"));
                if (!pList.isEmpty() && isKeyframesMode) {
                    Object imgObj = extraBody.get("image");
                    int imgCount = (imgObj instanceof java.util.List) ? ((java.util.List<?>) imgObj).size() : 1;
                    if (pList.size() == imgCount) {
                        extraBody.put("prompt_list", pList);
                    } else if (pList.size() >= imgCount) {
                        log.warn("[AgnesVideo] prompt_list({}) 数量与图片数({})不一致，截取前{}个匹配",
                                pList.size(), imgCount, imgCount);
                        extraBody.put("prompt_list", new ArrayList<>(pList.subList(0, imgCount)));
                    } else {
                        log.warn("[AgnesVideo] prompt_list({}) 少于图片数({})，按实际可用写入", pList.size(), imgCount);
                        extraBody.put("prompt_list", pList);
                    }
                }
            }
        }

        if (hasImageInput) {
            body.put("extra_body", extraBody);
        }

        return body;
    }

    /** 将图片集合统一转成 Agnes 可接受的 base64 data URI 数组（支持混合：URL / 已 Base64 / 本地路径） */
    private List<String> convertImageListToBase64(java.util.Collection<?> items) {
        List<String> result = new ArrayList<>(items.size());
        int idx = 0;
        for (Object item : items) {
            idx++;
            if (item == null) continue;
            String src = String.valueOf(item);
            try {
                String b64 = ensureBase64DataUri(src);
                if (b64 != null) {
                    result.add(b64);
                    log.info("[AgnesVideo] 关键帧{}转 Base64 成功，长度={}", idx, b64.length());
                } else {
                    log.warn("[AgnesVideo] 关键帧{}转 Base64 失败，已跳过：src={}", idx, truncate(src, 120));
                }
            } catch (Exception e) {
                log.warn("[AgnesVideo] 关键帧{}转 Base64 异常：{}，src={}", idx, e.getMessage(), truncate(src, 120));
            }
        }
        return result;
    }

    /**
     * 确保图片输入是 Agnes API 认可的 {@code data:image/png;base64,xxxx} 形式。
     * <ul>
     *   <li>已满足 data:image/... 前缀 → 原样返回</li>
     *   <li>http/https URL → 下载字节 → 读取 Content-Type → 拼接 Base64</li>
     *   <li>其他（本地路径/纯 Base64） → 暂不支持，返回 null</li>
     * </ul>
     */
    private String ensureBase64DataUri(String src) throws Exception {
        if (src == null || src.isEmpty()) return null;
        src = src.trim();

        // 已为 data URI → 直接返回
        if (src.startsWith("data:image/")) {
            return src;
        }

        // HTTP(S) URL → 下载 + MIME 探测 + Base64 编码
        if (src.startsWith("http://") || src.startsWith("https://")) {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(30))
                    .build();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(src))
                    .timeout(java.time.Duration.ofSeconds(120))
                    .GET()
                    .build();
            HttpResponse<InputStream> resp = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new RuntimeException("HTTP " + resp.statusCode());
            }
            // MIME：优先 Content-Type，兜底从 URL 后缀推断
            String mime = null;
            List<String> ct = resp.headers().allValues("Content-Type");
            if (ct != null && !ct.isEmpty()) {
                String raw = ct.get(0);
                int sc = raw.indexOf(';');
                mime = (sc > 0 ? raw.substring(0, sc) : raw).trim().toLowerCase();
            }
            if (mime == null || mime.isEmpty() || mime.startsWith("application/")) {
                mime = guessMimeFromUrl(src);
            }
            if (mime == null) mime = "image/png"; // 最终兜底

            try (InputStream is = resp.body();
                 ByteArrayOutputStream baos = new ByteArrayOutputStream(1024 * 512)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) > 0) baos.write(buf, 0, n);
                String b64 = Base64.getEncoder().encodeToString(baos.toByteArray());
                String result = "data:" + mime + ";base64," + b64;
                log.debug("[AgnesVideo] URL→Base64 完成，mime={}, size={}B, result_len={}",
                        mime, baos.size(), result.length());
                return result;
            }
        }

        // 不支持的形式（纯 Base64 字符串无 MIME / 本地路径）
        log.warn("[AgnesVideo] 图片输入格式无法识别（非 data: URI / 非 http(s) URL），已忽略：{}", truncate(src, 80));
        return null;
    }

    private static String guessMimeFromUrl(String url) {
        String lower = url.toLowerCase();
        int q = lower.indexOf('?');
        if (q > 0) lower = lower.substring(0, q);
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".bmp")) return "image/bmp";
        return null;
    }

    /**
     * 与 gen_video.py 一致的 API 帧数计算规则：
     * raw = duration * fps → n = floor((raw-1)/8) → if n*8+1 < raw then n+1 → result = n*8+1
     */
    private int calcTotalFrames(int duration, int fps) {
        int raw = duration * fps;
        int n = (raw - 1) / 8;
        if (n * 8 + 1 < raw) n += 1;
        return n * 8 + 1;
    }

    private HttpHeaders buildHeaders(AiModelContext context) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + context.getApiKey());
        return headers;
    }

    // ==================== 工具方法 ====================

    /** 解析查询地址：优先 extra.queryUrl，否则用 apiUrl 域下 /agnesapi */
    private String resolveQueryUrl(AiModelContext context, AiInvokeRequest request) {
        Map<String, Object> extra = request.getExtra();
        if (extra != null && extra.containsKey("queryUrl")) {
            String q = String.valueOf(extra.get("queryUrl"));
            if (StringUtils.hasText(q)) return q;
        }
        String apiUrl = context.getApiUrl() != null ? context.getApiUrl() : "";
        int idx = apiUrl.indexOf("/", 8); // 跳过 https://
        String domain = idx > 0 ? apiUrl.substring(0, idx) : apiUrl;
        return domain + "/agnesapi";
    }

    private boolean isNonRetryableError(int statusCode) {
        return statusCode == 400 || statusCode == 401 || statusCode == 403;
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
            String code = root.path("code").asText(null);
            String message = root.path("message").asText(null);
            if (code != null && !code.isEmpty() && message != null && !message.isEmpty()) {
                return "[" + code + "] " + message;
            }
            if (message != null && !message.isEmpty()) return message;
        } catch (Exception e) {
            log.debug("[AgnesVideo] 解析错误响应体失败: {}", e.getMessage());
        }
        return errorBody.length() > 300 ? errorBody.substring(0, 300) + "..." : errorBody;
    }

    private void sleepBeforeRetry(int attempt) {
        try {
            Thread.sleep(1000L * Math.min(attempt, 5));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /** 下载视频并上传到存储服务，返回签名访问 URL（不带鉴权头，避免 CDN 401） */
    private String downloadAndStoreVideo(String videoUrl, AiInvokeRequest request) throws Exception {
        String objectKey = (request.getTaskId() != null ? request.getTaskId() : "adhoc")
                + "/video/" + UUID.randomUUID() + ".mp4";

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(videoUrl))
                .GET()
                .build();
        HttpResponse<InputStream> response = client.send(httpRequest,
                HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException("下载视频失败，HTTP " + response.statusCode());
        }

        try (InputStream is = response.body()) {
            String storedKey = storageService.upload(is, objectKey, -1, "video/mp4");
            return storageService.signUrl(storedKey, 3600);
        }
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return null;
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }
}
