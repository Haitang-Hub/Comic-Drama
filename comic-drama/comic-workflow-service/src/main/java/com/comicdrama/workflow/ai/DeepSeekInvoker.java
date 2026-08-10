package com.comicdrama.workflow.ai;

import com.comicdrama.common.ai.AiInvokeRequest;
import com.comicdrama.common.ai.AiInvokeResponse;
import com.comicdrama.common.ai.AiModelContext;
import com.comicdrama.common.ai.StreamingAiModelInvoker;
import com.comicdrama.common.enums.ModelCapability;
import com.comicdrama.common.enums.ModelType;
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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * DeepSeek 文本模型调用器。
 * 对接 DeepSeek / ModelScope Chat Completions API（OpenAI 兼容格式）。
 * 支持 SSE 流式输出（{@link StreamingAiModelInvoker}），文本步骤可实时推送增量内容到前端。
 */
@Slf4j
@Component
public class DeepSeekInvoker implements StreamingAiModelInvoker {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /** 不可重试的 HTTP 状态码（客户端错误/配额耗尽/鉴权失败） */
    private static final int STATUS_BAD_REQUEST = 400;
    private static final int STATUS_UNAUTHORIZED = 401;
    private static final int STATUS_FORBIDDEN = 403;
    private static final int STATUS_QUOTA_EXCEEDED = 429;

    @Value("${ai.deepseek.timeout:30000}")
    private int timeout;

    @Value("${ai.deepseek.retry-times:2}")
    private int retryTimes;

    public DeepSeekInvoker(RestTemplate aiRestTemplate, ObjectMapper objectMapper) {
        this.restTemplate = aiRestTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(ModelType modelType) {
        return ModelType.TEXT.equals(modelType);
    }

    @Override
    public boolean supports(String modelProvider) {
        // 支持 deepseek 原生接口和 modelscope 上的 deepseek 文本模型
        return "deepseek".equals(modelProvider) || "modelscope".equals(modelProvider);
    }

    @Override
    public java.util.Set<String> supportedProtocols() {
        return java.util.Set.of("openai-chat");
    }

    @Override
    public java.util.Set<ModelType> supportedModelTypes() {
        return java.util.Set.of(ModelType.TEXT);
    }

    @Override
    public java.util.Set<ModelCapability> capabilities() {
        return java.util.Set.of(
                ModelCapability.STREAMING,
                ModelCapability.FUNCTION_CALLING,
                ModelCapability.LONG_CONTEXT
        );
    }

    @Override
    public AiInvokeResponse invoke(AiModelContext context, AiInvokeRequest request) {
        long start = System.currentTimeMillis();
        String url = context.getApiUrl() + "/chat/completions";
        String modelName = context.resolveApiModel();

        Map<String, Object> body = buildRequestBody(context, request);
        log.info("请求体构建完成: url={}, body={}", url, body);
        HttpHeaders headers = buildHeaders(context);

        // 用 ObjectMapper 手动序列化 body 为 JSON 字符串，避免 RestTemplate message converter
        // 未正确写入 Map body 的问题（实测 SimpleClientHttpRequestFactory + Map body 会发送空请求体）
        String jsonBody;
        try {
            jsonBody = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            log.error("请求体 JSON 序列化失败，model={}", modelName, e);
            return AiInvokeResponse.builder()
                    .success(false)
                    .errorMessage("请求体序列化失败: " + e.getMessage())
                    .build();
        }
        log.info("请求体 JSON: {}", jsonBody);

        int maxAttempts = retryTimes + 1;
        int attempt = 0;

        while (attempt < maxAttempts) {
            attempt++;
            try {
                HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);
                ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

                if (response.getStatusCode().is2xxSuccessful()) {
                    String content = parseContent(response.getBody());
                    long costMs = System.currentTimeMillis() - start;

                    log.info("AI 调用成功，model={}, nodeKey={}, cost={}ms, contentLen={}",
                            modelName, request.getNodeKey(), costMs,
                            content != null ? content.length() : 0);

                    // 推理模型可能返回空 content（推理过程耗尽 max_tokens）；
                    // 魔搭免费 API-Inference 不稳定，有时返回 HTTP 200 但 choices=null 的空响应。
                    // 遇到空内容时重试，而非直接失败。
                    if (content == null || content.isEmpty()) {
                        log.warn("AI 返回空内容，model={}, nodeKey={}, attempt={}/{}, responseBody={}",
                                modelName, request.getNodeKey(), attempt, maxAttempts,
                                truncate(response.getBody(), 1000));
                        if (attempt < maxAttempts) {
                            sleepBeforeRetry(attempt);
                            continue;
                        }
                        return AiInvokeResponse.builder()
                                .success(false)
                                .errorMessage(String.format(
                                        "AI 模型「%s」返回了空内容（重试 %d 次后仍失败）。可能原因："
                                                + "1) 推理模型输出被截断，请增大 max_tokens；"
                                                + "2) 模型未理解请求，请优化 Prompt 模板；"
                                                + "3) 模型服务不稳定，请稍后重试",
                                        modelName, retryTimes))
                                .costMs(costMs)
                                .rawResponse(response.getBody())
                                .build();
                    }

                    return AiInvokeResponse.builder()
                            .success(true)
                            .text(content)
                            .costMs(costMs)
                            .rawResponse(response.getBody())
                            .build();
                } else {
                    int statusCode = response.getStatusCode().value();
                    String errorDetail = parseErrorBody(response.getBody());
                    log.warn("AI 调用返回非成功状态，attempt={}/{}, status={}, model={}, body={}",
                            attempt, maxAttempts, statusCode, modelName, errorDetail);

                    // 对于不可重试的错误（429配额、400参数、401鉴权），立即终止
                    if (isNonRetryableError(statusCode)) {
                        return buildErrorResponse(statusCode, modelName, errorDetail, start);
                    }

                    // 可重试的 5xx 错误：sleep 后继续重试
                    if (attempt < maxAttempts) {
                        sleepBeforeRetry(attempt);
                    }
                }
            } catch (HttpStatusCodeException hsce) {
                int statusCode = hsce.getStatusCode().value();
                String errorBody = hsce.getResponseBodyAsString();
                String errorDetail = parseErrorBody(errorBody);
                long costMs = System.currentTimeMillis() - start;

                log.warn("AI 调用 HTTP 错误，attempt={}/{}, status={}, model={}, cost={}ms, detail={}",
                        attempt, maxAttempts, statusCode, modelName, costMs, errorDetail);

                // 不可重试的错误：立即返回
                if (isNonRetryableError(statusCode)) {
                    return buildErrorResponse(statusCode, modelName, errorDetail, start);
                }

                // 可重试的错误（5xx、网络超时等）
                if (attempt >= maxAttempts) {
                    return buildErrorResponse(statusCode, modelName, errorDetail, start);
                }

                sleepBeforeRetry(attempt);

            } catch (Exception e) {
                long costMs = System.currentTimeMillis() - start;
                log.warn("AI 调用网络异常，attempt={}/{}, cost={}ms, model={}, error={}",
                        attempt, maxAttempts, costMs, modelName, e.getMessage());

                if (attempt >= maxAttempts) {
                    return AiInvokeResponse.fail("AI 调用失败（重试" + retryTimes + "次后）：" + e.getMessage());
                }

                sleepBeforeRetry(attempt);
            }
        }

        return AiInvokeResponse.fail("AI 调用失败：未知错误");
    }

    // ==================== 流式调用（SSE） ====================

    @Override
    public AiInvokeResponse invokeStream(AiModelContext context, AiInvokeRequest request,
                                         Consumer<String> chunkConsumer) {
        long start = System.currentTimeMillis();
        String url = context.getApiUrl() + "/chat/completions";
        String modelName = context.resolveApiModel();

        // 构建流式请求体（与非流式一致，仅增加 stream=true）
        Map<String, Object> body = buildRequestBody(context, request);
        body.put("stream", true);

        String jsonBody;
        try {
            jsonBody = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            log.error("流式请求体序列化失败，model={}", modelName, e);
            return AiInvokeResponse.fail("请求体序列化失败: " + e.getMessage());
        }

        log.info("流式 AI 调用开始：model={}, nodeKey={}, url={}", modelName, request.getNodeKey(), url);

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(30))
                    .build();

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(timeout > 0 ? timeout : 60))
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .header("Authorization", "Bearer " + context.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();

            // 使用 ofLines 获取逐行响应，实现真正的流式处理
            HttpResponse<java.util.stream.Stream<String>> response =
                    client.send(httpRequest, HttpResponse.BodyHandlers.ofLines());

            int statusCode = response.statusCode();
            if (statusCode != 200) {
                String errorBody = response.body()
                        .collect(java.util.stream.Collectors.joining("\n"));
                long costMs = System.currentTimeMillis() - start;
                log.warn("流式调用返回非 200：status={}, model={}, body={}", statusCode, modelName,
                        truncate(errorBody, 500));
                return buildErrorResponse(statusCode, modelName,
                        parseErrorBody(errorBody), start);
            }

            // 逐行解析 SSE data 行，提取增量内容
            StringBuilder fullContent = new StringBuilder();

            response.body().forEach(line -> {
                if (line.startsWith("data: ")) {
                    String data = line.substring(6).trim();
                    if ("[DONE]".equals(data)) {
                        return;
                    }
                    try {
                        JsonNode root = objectMapper.readTree(data);
                        JsonNode choices = root.path("choices");
                        if (choices.isArray() && !choices.isEmpty()) {
                            JsonNode delta = choices.get(0).path("delta");
                            String content = delta.path("content").asText(null);
                            if (content != null && !content.isEmpty()) {
                                fullContent.append(content);
                                chunkConsumer.accept(content);
                            }
                        }
                    } catch (Exception e) {
                        log.debug("SSE 行解析失败：{}", data);
                    }
                }
            });

            long costMs = System.currentTimeMillis() - start;
            String text = fullContent.toString();

            if (text.isEmpty()) {
                log.warn("流式调用返回空内容，model={}, nodeKey={}", modelName, request.getNodeKey());
                return AiInvokeResponse.builder()
                        .success(false)
                        .errorMessage("AI 模型「" + modelName + "」流式返回空内容")
                        .costMs(costMs)
                        .build();
            }

            log.info("流式 AI 调用成功：model={}, nodeKey={}, cost={}ms, contentLen={}",
                    modelName, request.getNodeKey(), costMs, text.length());

            return AiInvokeResponse.builder()
                    .success(true)
                    .text(text)
                    .costMs(costMs)
                    .build();

        } catch (Exception e) {
            long costMs = System.currentTimeMillis() - start;
            log.error("流式调用异常：model={}, error={}", modelName, e.getMessage());
            return AiInvokeResponse.builder()
                    .success(false)
                    .errorMessage("流式调用失败：" + e.getMessage())
                    .costMs(costMs)
                    .build();
        }
    }

    /**
     * 判断是否为不可重试的 HTTP 错误。
     * 400/401/403/429 这类错误重试无意义，应立即终止并告知用户。
     */
    private boolean isNonRetryableError(int statusCode) {
        return statusCode == STATUS_BAD_REQUEST
                || statusCode == STATUS_UNAUTHORIZED
                || statusCode == STATUS_FORBIDDEN
                || statusCode == STATUS_QUOTA_EXCEEDED;
    }

    /**
     * 构建错误响应，包含清晰的中文错误提示。
     */
    private AiInvokeResponse buildErrorResponse(int statusCode, String modelName,
                                                 String errorDetail, long startTime) {
        long costMs = System.currentTimeMillis() - startTime;
        log.debug("AI 调用错误耗时: {}ms", costMs);
        String userMessage;

        switch (statusCode) {
            case STATUS_QUOTA_EXCEEDED:
                userMessage = String.format(
                        "AI 模型「%s」今日配额已耗尽（HTTP 429）。%n建议：1) 等待明日配额重置；2) 在系统设置中切换到其他文本模型（如 deepseek 官方 API）；3) 升级 API 套餐",
                        modelName);
                log.error("AI 模型配额耗尽！model={}, status=429, detail={}", modelName, errorDetail);
                break;
            case STATUS_BAD_REQUEST:
                userMessage = String.format("AI 模型「%s」请求参数错误（HTTP 400）：%s", modelName, errorDetail);
                break;
            case STATUS_UNAUTHORIZED:
                userMessage = String.format("AI 模型「%s」API 密钥无效（HTTP 401），请在系统设置中检查 API Key 配置", modelName);
                break;
            case STATUS_FORBIDDEN:
                userMessage = String.format("AI 模型「%s」无访问权限（HTTP 403）：%s", modelName, errorDetail);
                break;
            default:
                userMessage = String.format("AI 模型「%s」调用失败（HTTP %d）：%s", modelName, statusCode, errorDetail);
                break;
        }

        return AiInvokeResponse.fail(userMessage);
    }

    /**
     * 从 API 错误响应体中提取可读的错误描述。
     */
    private String parseErrorBody(String errorBody) {
        if (errorBody == null || errorBody.isEmpty()) {
            return "无详细错误信息";
        }
        try {
            JsonNode root = objectMapper.readTree(errorBody);
            // 尝试提取 error.message 字段
            JsonNode errorNode = root.path("error");
            if (!errorNode.isMissingNode()) {
                String message = errorNode.path("message").asText(null);
                if (message != null && !message.isEmpty()) {
                    return message;
                }
                String msg = errorNode.asText(null);
                if (msg != null && !msg.isEmpty()) {
                    return msg;
                }
            }
            // 直接取 message 字段
            String message = root.path("message").asText(null);
            if (message != null && !message.isEmpty()) {
                return message;
            }
        } catch (Exception e) {
            log.debug("解析错误响应体失败: {}", errorBody);
        }
        // 返回原始内容（截断到200字符）
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

        List<Map<String, String>> messages = new ArrayList<>();
        if (StringUtils.hasText(request.getSystemPrompt())) {
            messages.add(Map.of("role", "system", "content", request.getSystemPrompt()));
        }
        messages.add(Map.of("role", "user", "content", request.getPrompt()));
        body.put("messages", messages);

        Map<String, Object> extra = request.getExtra();
        if (extra != null && extra.containsKey("temperature")) {
            body.put("temperature", extra.get("temperature"));
        } else {
            body.put("temperature", 0.7);
        }
        if (extra != null && extra.containsKey("max_tokens")) {
            body.put("max_tokens", extra.get("max_tokens"));
        } else {
            // max_tokens 默认值按服务商区分：
            // - modelscope 免费API-Inference 实测 max_tokens 上限约 8192，超过会返回 choices=null 的空响应
            // - deepseek 等原生 API 支持较大 max_tokens（推理模型推理过程可能消耗 30000+ tokens）
            boolean isModelScope = "modelscope".equalsIgnoreCase(context.getModelProvider());
            body.put("max_tokens", isModelScope ? 8192 : 32768);
        }

        // 关闭 Qwen3 系列模型的思维链（thinking）模式。
        // Qwen3 默认 enable_thinking=True，非流式请求+思维链会返回 choices=null 的空响应（魔搭免费API-Inference 实测）。
        // 关闭后模型直接返回最终回答到 content，非流式可正常工作。
        // 对魔搭上的 DeepSeek 系列模型（如 DeepSeek-V4-Pro），此参数会被忽略，无副作用。
        // 注：OpenAI SDK 的 extra_body 在 HTTP 层即顶层 JSON 字段，此处顶层传参等效。
        body.put("enable_thinking", false);

        return body;
    }

    private HttpHeaders buildHeaders(AiModelContext context) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + context.getApiKey());
        return headers;
    }

    private String parseContent(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                JsonNode message = choices.get(0).path("message");
                String content = message.path("content").asText(null);

                // content 有内容，直接返回
                if (content != null && !content.isEmpty()) {
                    return content;
                }

                // content 为空，检查是否为推理模型（如 Qwen3 / deepseek-v4-flash）
                // 推理模型会先输出 reasoning_content（推理过程），再输出 content（最终回答）
                String reasoning = message.path("reasoning_content").asText(null);
                String finishReason = choices.get(0).path("finish_reason").asText(null);

                if (reasoning != null && !reasoning.isEmpty()) {
                    if ("length".equals(finishReason)) {
                        // 推理过程耗尽了 max_tokens，最终回答未生成
                        log.warn("推理模型输出被截断：finish_reason=length, reasoningLen={}, content 为空。"
                                        + "建议：增大 max_tokens 或确认请求已设置 enable_thinking=false",
                                reasoning.length());
                    } else {
                        // 有推理内容但无最终回答
                        log.warn("推理模型未生成最终回答：finish_reason={}, reasoningLen={}, content 为空",
                                finishReason, reasoning.length());
                    }
                } else {
                    log.warn("AI 返回空内容且无推理过程：finish_reason={}", finishReason);
                }

                // 打印响应体摘要用于排查（截断避免日志过大）
                log.warn("空内容响应体摘要: {}", truncate(responseBody, 2000));

                return content;
            }
        } catch (Exception e) {
            log.warn("解析 AI 响应体失败: {}", responseBody, e);
        }
        return null;
    }

    /**
     * 截断字符串到指定长度，超出部分用 ... 标记，避免日志输出过大。
     */
    private String truncate(String text, int maxLen) {
        if (text == null) {
            return null;
        }
        return text.length() > maxLen ? text.substring(0, maxLen) + "...(truncated)" : text;
    }
}