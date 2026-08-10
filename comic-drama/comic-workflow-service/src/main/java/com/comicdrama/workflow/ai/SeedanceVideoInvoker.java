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

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Seedance 视频模型调用器。
 * 支持多模态视频生成（文生视频 / 图生视频），支持帧参考承接（首帧锁定）。
 * 视频生成是异步流程，采用轮询策略获取最终结果。
 */
@Slf4j
@Component
public class SeedanceVideoInvoker implements AiModelInvoker {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final StorageService storageService;

    @Value("${ai.seedance.timeout:120000}")
    private int timeout;

    @Value("${ai.seedance.retry-times:3}")
    private int retryTimes;

    @Value("${ai.seedance.poll-interval:3000}")
    private long pollInterval;

    @Value("${ai.seedance.poll-max-attempts:60}")
    private int pollMaxAttempts;

    public SeedanceVideoInvoker(RestTemplate aiRestTemplate, ObjectMapper objectMapper, StorageService storageService) {
        this.restTemplate = aiRestTemplate;
        this.objectMapper = objectMapper;
        this.storageService = storageService;
    }

    @Override
    public boolean supports(ModelType modelType) {
        return ModelType.VIDEO.equals(modelType);
    }

    @Override
    public boolean supports(String modelProvider) {
        return "seedance_video".equals(modelProvider);
    }

    @Override
    public java.util.Set<String> supportedProtocols() {
        return java.util.Set.of("ark-video");
    }

    @Override
    public java.util.Set<ModelType> supportedModelTypes() {
        return java.util.Set.of(ModelType.VIDEO);
    }

    @Override
    public java.util.Set<ModelCapability> capabilities() {
        return java.util.Set.of(ModelCapability.FIRST_FRAME_LOCK);
    }

    @Override
    public AiInvokeResponse invoke(AiModelContext context, AiInvokeRequest request) {
        long start = System.currentTimeMillis();
        String url = context.getApiUrl() + "/videos/generations";
        String modelName = context.resolveApiModel();

        Map<String, Object> body = buildRequestBody(context, request);
        HttpHeaders headers = buildHeaders(context);

        int maxAttempts = retryTimes + 1;
        int attempt = 0;

        while (attempt < maxAttempts) {
            attempt++;
            try {
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
                ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

                if (response.getStatusCode().is2xxSuccessful()) {
                    JsonNode root = objectMapper.readTree(response.getBody());
                    String taskId = root.path("id").asText(null);

                    if (!StringUtils.hasText(taskId)) {
                        return AiInvokeResponse.fail("视频生成响应中未找到任务 ID");
                    }

                    log.info("视频任务已提交，taskId={}, model={}, nodeKey={}",
                            taskId, modelName, request.getNodeKey());

                    return pollForResult(context, request, taskId, start);
                } else {
                    int statusCode = response.getStatusCode().value();
                    String errorDetail = parseErrorBody(response.getBody());
                    log.warn("视频生成调用返回非成功状态，attempt={}/{}, status={}, model={}, body={}",
                            attempt, maxAttempts, statusCode, modelName, errorDetail);

                    if (isNonRetryableError(statusCode)) {
                        return buildErrorResponse(statusCode, modelName, errorDetail);
                    }
                    if (attempt < maxAttempts) {
                        sleepBeforeRetry(attempt);
                    }
                }
            } catch (HttpStatusCodeException hsce) {
                int statusCode = hsce.getStatusCode().value();
                String errorDetail = parseErrorBody(hsce.getResponseBodyAsString());
                long costMs = System.currentTimeMillis() - start;

                log.warn("视频生成调用 HTTP 错误，attempt={}/{}, status={}, model={}, cost={}ms, detail={}",
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
                log.warn("视频生成调用网络异常，attempt={}/{}, cost={}ms, model={}, error={}",
                        attempt, maxAttempts, costMs, modelName, e.getMessage());

                if (attempt >= maxAttempts) {
                    return AiInvokeResponse.fail("视频生成调用失败（重试" + retryTimes + "次后）：" + e.getMessage());
                }
                sleepBeforeRetry(attempt);
            }
        }

        return AiInvokeResponse.fail("视频生成调用失败：未知错误");
    }

    private boolean isNonRetryableError(int statusCode) {
        return statusCode == 400 || statusCode == 401 || statusCode == 403 || statusCode == 429;
    }

    private AiInvokeResponse buildErrorResponse(int statusCode, String modelName, String errorDetail) {
        String userMessage;
        switch (statusCode) {
            case 429:
                userMessage = String.format("视频模型「%s」今日配额已耗尽（HTTP 429）。请等待明日配额重置或在系统设置中切换到其他模型。", modelName);
                break;
            case 400:
                userMessage = String.format("视频模型「%s」请求参数错误（HTTP 400）：%s", modelName, errorDetail);
                break;
            case 401:
                userMessage = String.format("视频模型「%s」API 密钥无效（HTTP 401），请检查 API Key 配置", modelName);
                break;
            default:
                userMessage = String.format("视频模型「%s」调用失败（HTTP %d）：%s", modelName, statusCode, errorDetail);
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

    private AiInvokeResponse pollForResult(AiModelContext context, AiInvokeRequest request,
                                            String taskId, long globalStart) {
        String statusUrl = context.getApiUrl() + "/videos/generations/" + taskId;
        HttpHeaders headers = buildHeaders(context);
        int pollAttempts = 0;

        while (pollAttempts < pollMaxAttempts) {
            pollAttempts++;

            try {
                Thread.sleep(pollInterval);

                HttpEntity<Void> entity = new HttpEntity<>(headers);
                ResponseEntity<String> response = restTemplate.exchange(statusUrl,
                        org.springframework.http.HttpMethod.GET, entity, String.class);

                if (response.getStatusCode().is2xxSuccessful()) {
                    JsonNode root = objectMapper.readTree(response.getBody());
                    String status = root.path("status").asText();

                    switch (status) {
                        case "succeeded":
                            String videoUrl = parseVideoUrl(root);
                            long costMs = System.currentTimeMillis() - globalStart;

                            if (!StringUtils.hasText(videoUrl)) {
                                return AiInvokeResponse.fail("视频生成成功但未找到视频 URL");
                            }

                            String storedUrl = downloadAndStoreVideo(videoUrl, request);

                            log.info("Seedance 视频生成成功，taskId={}, nodeKey={}, cost={}ms, size={}",
                                    taskId, request.getNodeKey(), costMs,
                                    root.path("video_url").path("width").asInt(0) + "x"
                                            + root.path("video_url").path("height").asInt(0));

                            Map<String, Object> extra = new HashMap<>();
                            extra.put("width", root.path("video_url").path("width").asInt(0));
                            extra.put("height", root.path("video_url").path("height").asInt(0));
                            extra.put("duration", root.path("video_url").path("duration").asInt(0));

                            return AiInvokeResponse.builder()
                                    .success(true)
                                    .resourceUrl(storedUrl)
                                    .resourceType("video")
                                    .costMs(costMs)
                                    .rawResponse(response.getBody())
                                    .extra(extra)
                                    .build();

                        case "failed":
                            String errorMsg = root.path("error").path("message").asText("视频生成失败");
                            log.error("Seedance 视频生成失败，taskId={}, error={}", taskId, errorMsg);
                            return AiInvokeResponse.fail(errorMsg);

                        case "processing":
                        case "queued":
                        default:
                            if (pollAttempts % 5 == 0) {
                                log.info("Seedance 视频生成中，taskId={}, status={}, pollAttempts={}",
                                        taskId, status, pollAttempts);
                            }
                            break;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return AiInvokeResponse.fail("Seedance 轮询被中断");
            } catch (Exception e) {
                log.warn("Seedance 轮询异常，taskId={}, pollAttempts={}, error={}",
                        taskId, pollAttempts, e.getMessage());
            }
        }

        log.error("Seedance 视频生成轮询超时，taskId={}, maxAttempts={}", taskId, pollMaxAttempts);
        return AiInvokeResponse.fail("视频生成轮询超时（" + pollMaxAttempts + "次后）");
    }

    private Map<String, Object> buildRequestBody(AiModelContext context, AiInvokeRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", context.resolveApiModel());
        body.put("prompt", request.getPrompt());

        Map<String, Object> extra = request.getExtra();

        if (StringUtils.hasText(request.getReferenceImageUrl())) {
            body.put("first_frame_image", request.getReferenceImageUrl());
            if (extra != null && extra.containsKey("video_strength")) {
                body.put("video_strength", extra.get("video_strength"));
            } else {
                body.put("video_strength", 0.5f);
            }
        }

        if (extra != null && extra.containsKey("duration")) {
            body.put("duration", extra.get("duration"));
        } else {
            body.put("duration", 5);
        }

        return body;
    }

    private HttpHeaders buildHeaders(AiModelContext context) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + context.getApiKey());
        return headers;
    }

    private String parseVideoUrl(JsonNode root) {
        JsonNode data = root.path("data");
        if (data.isArray() && !data.isEmpty()) {
            return data.get(0).path("url").asText(null);
        }
        String directUrl = root.path("video_url").path("url").asText(null);
        if (StringUtils.hasText(directUrl)) {
            return directUrl;
        }
        return null;
    }

    private String downloadAndStoreVideo(String videoUrl, AiInvokeRequest request) throws Exception {
        String objectKey = request.getTaskId() + "/video/" + UUID.randomUUID() + ".mp4";

        HttpClient client = HttpClient.newHttpClient();
        java.net.http.HttpRequest httpRequest = java.net.http.HttpRequest.newBuilder()
                .uri(URI.create(videoUrl))
                .GET()
                .build();
        HttpResponse<InputStream> response = client.send(httpRequest,
                HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            throw new RuntimeException("下载视频失败，状态码: " + response.statusCode());
        }

        try (InputStream is = response.body()) {
            String storedKey = storageService.upload(is, objectKey, -1, "video/mp4");
            return storageService.signUrl(storedKey, 3600);
        }
    }
}