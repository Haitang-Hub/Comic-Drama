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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Seed-TTS 音频模型调用器。
 * 对接字节 Seed-TTS 语音合成 API，支持音色克隆与语速调节。
 */
@Slf4j
@Component
public class SeedTtsInvoker implements AiModelInvoker {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final StorageService storageService;

    @Value("${ai.seed-tts.timeout:30000}")
    private int timeout;

    @Value("${ai.seed-tts.retry-times:2}")
    private int retryTimes;

    public SeedTtsInvoker(RestTemplate aiRestTemplate, ObjectMapper objectMapper, StorageService storageService) {
        this.restTemplate = aiRestTemplate;
        this.objectMapper = objectMapper;
        this.storageService = storageService;
    }

    @Override
    public boolean supports(ModelType modelType) {
        return ModelType.AUDIO.equals(modelType);
    }

    @Override
    public boolean supports(String modelProvider) {
        return "seed_tts".equals(modelProvider);
    }

    @Override
    public java.util.Set<String> supportedProtocols() {
        return java.util.Set.of("ark-tts");
    }

    @Override
    public java.util.Set<ModelType> supportedModelTypes() {
        return java.util.Set.of(ModelType.AUDIO);
    }

    @Override
    public java.util.Set<ModelCapability> capabilities() {
        return java.util.Set.of(ModelCapability.MULTI_VOICE);
    }

    @Override
    public AiInvokeResponse invoke(AiModelContext context, AiInvokeRequest request) {
        long start = System.currentTimeMillis();
        String url = context.getApiUrl() + "/tts";
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
                    AiInvokeResponse result = parseAudioResponse(response.getBody(), context, request);
                    long costMs = System.currentTimeMillis() - start;
                    result.setCostMs(costMs);

                    log.info("TTS 调用成功，model={}, nodeKey={}, cost={}ms",
                            modelName, request.getNodeKey(), costMs);

                    return result;
                } else {
                    int statusCode = response.getStatusCode().value();
                    String errorDetail = parseErrorBody(response.getBody());
                    log.warn("TTS 调用返回非成功状态，attempt={}/{}, status={}, model={}, body={}",
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

                log.warn("TTS 调用 HTTP 错误，attempt={}/{}, status={}, model={}, cost={}ms, detail={}",
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
                log.warn("TTS 调用网络异常，attempt={}/{}, cost={}ms, model={}, error={}",
                        attempt, maxAttempts, costMs, modelName, e.getMessage());

                if (attempt >= maxAttempts) {
                    return AiInvokeResponse.fail("TTS 调用失败（重试" + retryTimes + "次后）：" + e.getMessage());
                }
                sleepBeforeRetry(attempt);
            }
        }

        return AiInvokeResponse.fail("TTS 调用失败：未知错误");
    }

    private boolean isNonRetryableError(int statusCode) {
        return statusCode == 400 || statusCode == 401 || statusCode == 403 || statusCode == 429;
    }

    private AiInvokeResponse buildErrorResponse(int statusCode, String modelName, String errorDetail) {
        String userMessage;
        switch (statusCode) {
            case 429:
                userMessage = String.format("语音模型「%s」今日配额已耗尽（HTTP 429）。请等待明日配额重置或在系统设置中切换到其他模型。", modelName);
                break;
            case 400:
                userMessage = String.format("语音模型「%s」请求参数错误（HTTP 400）：%s", modelName, errorDetail);
                break;
            case 401:
                userMessage = String.format("语音模型「%s」API 密钥无效（HTTP 401），请检查 API Key 配置", modelName);
                break;
            default:
                userMessage = String.format("语音模型「%s」调用失败（HTTP %d）：%s", modelName, statusCode, errorDetail);
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

        String text = StringUtils.hasText(request.getText()) ? request.getText() : request.getPrompt();
        body.put("text", text);

        if (StringUtils.hasText(request.getVoiceMaterialCode())) {
            body.put("voice", request.getVoiceMaterialCode());
        }

        Map<String, Object> extra = request.getExtra();
        if (extra != null && extra.containsKey("speed")) {
            body.put("speed", extra.get("speed"));
        } else {
            body.put("speed", 1.0);
        }

        return body;
    }

    private HttpHeaders buildHeaders(AiModelContext context) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + context.getApiKey());
        return headers;
    }

    private AiInvokeResponse parseAudioResponse(String responseBody, AiModelContext context, AiInvokeRequest request) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            String audioUrl = root.path("data").path("url").asText(null);
            if (StringUtils.hasText(audioUrl)) {
                String storedUrl = storeAudioFromUrl(audioUrl, request);
                return AiInvokeResponse.builder()
                        .success(true)
                        .resourceUrl(storedUrl)
                        .resourceType("audio")
                        .rawResponse(responseBody)
                        .build();
            }

            String base64Audio = root.path("data").path("audio").asText(null);
            if (StringUtils.hasText(base64Audio)) {
                String storedUrl = storeAudioFromBase64(base64Audio, request);
                return AiInvokeResponse.builder()
                        .success(true)
                        .resourceUrl(storedUrl)
                        .resourceType("audio")
                        .rawResponse(responseBody)
                        .build();
            }

        } catch (Exception e) {
            log.warn("解析 SeedTTS 响应体失败: {}", responseBody, e);
        }

        return AiInvokeResponse.fail("SeedTTS 响应解析失败：未找到音频数据");
    }

    private String storeAudioFromUrl(String audioUrl, AiInvokeRequest request) throws Exception {
        String objectKey = request.getTaskId() + "/audio/" + UUID.randomUUID() + ".mp3";

        HttpClient client = HttpClient.newHttpClient();
        java.net.http.HttpRequest httpRequest = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(audioUrl))
                .GET()
                .build();
        java.net.http.HttpResponse<java.io.InputStream> response = client.send(httpRequest,
                java.net.http.HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            throw new RuntimeException("下载音频失败，状态码: " + response.statusCode());
        }

        try (InputStream is = response.body()) {
            String storedKey = storageService.upload(is, objectKey, -1, "audio/mpeg");
            return storageService.signUrl(storedKey, 3600);
        }
    }

    private String storeAudioFromBase64(String base64Audio, AiInvokeRequest request) throws Exception {
        String objectKey = request.getTaskId() + "/audio/" + UUID.randomUUID() + ".mp3";
        byte[] audioBytes = Base64.getDecoder().decode(base64Audio);

        try (InputStream is = new ByteArrayInputStream(audioBytes)) {
            String storedKey = storageService.upload(is, objectKey, audioBytes.length, "audio/mpeg");
            return storageService.signUrl(storedKey, 3600);
        }
    }
}