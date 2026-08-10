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
 * Seedream 图像模型调用器。
 * 支持文生图（text-to-image）与图生图（image-to-image，通过 referenceImageUrl 判断）。
 */
@Slf4j
@Component
public class SeedreamInvoker implements AiModelInvoker {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final StorageService storageService;

    @Value("${ai.seedream.timeout:60000}")
    private int timeout;

    @Value("${ai.seedream.retry-times:2}")
    private int retryTimes;

    /**
     * 默认图像尺寸 2560x1440（3,686,400 像素）。
     * 满足 seedream 模型最小像素要求（3686400）。
     */
    private static final String DEFAULT_IMAGE_SIZE = "2560x1440";

    public SeedreamInvoker(RestTemplate aiRestTemplate, ObjectMapper objectMapper,
                           StorageService storageService) {
        this.restTemplate = aiRestTemplate;
        this.objectMapper = objectMapper;
        this.storageService = storageService;
    }

    @Override
    public boolean supports(ModelType modelType) {
        return ModelType.IMAGE.equals(modelType);
    }

    @Override
    public boolean supports(String modelProvider) {
        return "seedream".equals(modelProvider);
    }

    @Override
    public java.util.Set<String> supportedProtocols() {
        return java.util.Set.of("ark-image");
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
        String url = context.getApiUrl() + "/images/generations";
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
                    String imageUrl = parseImageUrl(response.getBody());
                    if (!StringUtils.hasText(imageUrl)) {
                        return AiInvokeResponse.fail("图像生成响应中未找到图片 URL");
                    }

                    String storedUrl = downloadAndStore(imageUrl, context, request);
                    long costMs = System.currentTimeMillis() - start;

                    log.info("图像生成调用成功，model={}, nodeKey={}, cost={}ms, imageUrl={}",
                            modelName, request.getNodeKey(), costMs, storedUrl);

                    return AiInvokeResponse.builder()
                            .success(true)
                            .resourceUrl(storedUrl)
                            .resourceType("image")
                            .costMs(costMs)
                            .rawResponse(response.getBody())
                            .build();
                } else {
                    int statusCode = response.getStatusCode().value();
                    String errorDetail = parseErrorBody(response.getBody());
                    log.warn("图像生成调用返回非成功状态，attempt={}/{}, status={}, model={}, body={}",
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

                log.warn("图像生成调用 HTTP 错误，attempt={}/{}, status={}, model={}, cost={}ms, detail={}",
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
                log.warn("图像生成调用网络异常，attempt={}/{}, cost={}ms, model={}, error={}",
                        attempt, maxAttempts, costMs, modelName, e.getMessage());

                if (attempt >= maxAttempts) {
                    return AiInvokeResponse.fail("图像生成调用失败（重试" + retryTimes + "次后）：" + e.getMessage());
                }
                sleepBeforeRetry(attempt);
            }
        }

        return AiInvokeResponse.fail("图像生成调用失败：未知错误");
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
        if (extra != null && extra.containsKey("size")) {
            body.put("size", extra.get("size"));
        } else {
            body.put("size", DEFAULT_IMAGE_SIZE);
        }
        body.put("n", 1);

        if (StringUtils.hasText(request.getReferenceImageUrl())) {
            body.put("reference_image", request.getReferenceImageUrl());
            if (extra != null && extra.containsKey("strength")) {
                body.put("strength", extra.get("strength"));
            } else {
                body.put("strength", 0.6);
            }
        }

        return body;
    }

    private HttpHeaders buildHeaders(AiModelContext context) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + context.getApiKey());
        return headers;
    }

    private String parseImageUrl(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode data = root.path("data");
            if (data.isArray() && !data.isEmpty()) {
                return data.get(0).path("url").asText();
            }
        } catch (Exception e) {
            log.warn("解析 Seedream 响应体失败: {}", responseBody, e);
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
        HttpClient client = HttpClient.newHttpClient();
        java.net.http.HttpRequest httpRequest = java.net.http.HttpRequest.newBuilder()
                .uri(URI.create(imageUrl))
                .GET()
                .build();
        HttpResponse<java.io.InputStream> response = client.send(httpRequest,
                java.net.http.HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            throw new RuntimeException("下载图片失败，状态码: " + response.statusCode());
        }
        return response.body();
    }
}