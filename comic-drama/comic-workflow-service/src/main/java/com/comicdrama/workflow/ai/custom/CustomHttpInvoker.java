package com.comicdrama.workflow.ai.custom;

import com.comicdrama.common.ai.AiInvokeRequest;
import com.comicdrama.common.ai.AiInvokeResponse;
import com.comicdrama.common.ai.AiModelContext;
import com.comicdrama.common.ai.AiModelInvoker;
import com.comicdrama.common.enums.ModelCapability;
import com.comicdrama.common.enums.ModelType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 配置驱动万能 HTTP 适配器。
 * <p>
 * 通过 YAML 模板定义 HTTP 请求/响应格式，支持任意 HTTP API 接入，无需写 Java 代码。
 * protocol 值格式：{@code custom-http-{模板名}}，运行时按模板名从 {@link CustomHttpTemplateLoader} 查找模板。
 *
 * <h3>工作流程</h3>
 * <ol>
 *   <li>启动时从 {@link CustomHttpTemplateLoader} 获取所有模板，声明为支持的协议</li>
 *   <li>路由命中后，从 protocol 提取模板名，加载模板</li>
 *   <li>将 AiInvokeRequest 字段填充到模板的 headers/body 占位符</li>
 *   <li>发送 HTTP 请求，按模板的 response 配置解析响应</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomHttpInvoker implements AiModelInvoker {

    private final CustomHttpTemplateLoader templateLoader;
    private final ObjectMapper objectMapper;

    /** 变量占位符正则：{{variableName}} */
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{(\\w+(?:\\.\\w+)?)}}");

    /** HTTP 调用超时（秒） */
    private static final int HTTP_TIMEOUT_SECONDS = 60;

    // ==================== 协议路由声明 ====================

    @Override
    public Set<String> supportedProtocols() {
        // 所有模板注册为 custom-http-{name} 协议
        return templateLoader.all().keySet().stream()
                .map(name -> "custom-http-" + name)
                .collect(Collectors.toSet());
    }

    @Override
    public Set<ModelType> supportedModelTypes() {
        // 注册全部类型，运行时由模板的 modelTypes 校验
        return EnumSet.allOf(ModelType.class);
    }

    @Override
    public Set<ModelCapability> capabilities() {
        // 聚合所有模板的静态能力声明
        Set<ModelCapability> caps = new HashSet<>();
        for (CustomHttpTemplate t : templateLoader.all().values()) {
            if (t.getCapabilities() != null) {
                for (String c : t.getCapabilities()) {
                    ModelCapability cap = ModelCapability.fromCode(c);
                    if (cap != null) {
                        caps.add(cap);
                    }
                }
            }
        }
        return caps;
    }

    // ==================== 旧路由 fallback ====================

    @Override
    public boolean supports(ModelType modelType) {
        return !templateLoader.all().isEmpty();
    }

    @Override
    public boolean supports(String modelProvider) {
        return false;
    }

    // ==================== 调用 ====================

    @Override
    public AiInvokeResponse invoke(AiModelContext context, AiInvokeRequest request) {
        long start = System.currentTimeMillis();
        String protocol = context.getProtocol();

        // 1. 从 protocol 提取模板名
        String templateName = extractTemplateName(protocol);
        if (templateName == null) {
            return AiInvokeResponse.fail("非法的 custom-http 协议格式：" + protocol);
        }

        CustomHttpTemplate template = templateLoader.get(templateName);
        if (template == null) {
            return AiInvokeResponse.fail("自定义 HTTP 模板不存在：" + templateName);
        }

        // 2. 校验模型类型
        if (template.getModelTypes() != null && !template.getModelTypes().isEmpty()
                && context.getModelType() != null
                && !template.getModelTypes().contains(context.getModelType())) {
            return AiInvokeResponse.fail(String.format(
                    "模板[%s]不支持模型类型[%d]，支持的类型：%s",
                    templateName, context.getModelType(), template.getModelTypes()));
        }

        // 3. 构建变量上下文
        Map<String, String> variables = buildVariables(context, request);

        // 4. 构建并发送 HTTP 请求
        try {
            HttpResponse<String> response = sendRequest(context, template, variables);
            long costMs = System.currentTimeMillis() - start;

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return parseSuccessResponse(template, response.body(), costMs);
            } else {
                String errorMsg = extractError(template, response.body());
                return AiInvokeResponse.builder()
                        .success(false)
                        .costMs(costMs)
                        .errorMessage(String.format("HTTP %d：%s", response.statusCode(), errorMsg))
                        .rawResponse(truncate(response.body(), 500))
                        .build();
            }
        } catch (Exception e) {
            long costMs = System.currentTimeMillis() - start;
            log.error("CustomHttpInvoker 调用异常：template={}, error={}", templateName, e.getMessage());
            return AiInvokeResponse.builder()
                    .success(false)
                    .costMs(costMs)
                    .errorMessage("HTTP 请求失败：" + e.getMessage())
                    .build();
        }
    }

    // ==================== 内部方法 ====================

    /**
     * 从 protocol 提取模板名：custom-http-myapi → myapi。
     */
    private String extractTemplateName(String protocol) {
        if (protocol == null || !protocol.startsWith("custom-http-")) {
            return null;
        }
        return protocol.substring("custom-http-".length());
    }

    /**
     * 构建变量上下文（从 AiModelContext + AiInvokeRequest 字段提取）。
     */
    private Map<String, String> buildVariables(AiModelContext context, AiInvokeRequest request) {
        Map<String, String> vars = new HashMap<>();
        vars.put("apiKey", context.getApiKey() != null ? context.getApiKey() : "");
        vars.put("apiUrl", context.getApiUrl() != null ? context.getApiUrl() : "");
        vars.put("modelName", context.resolveApiModel() != null ? context.resolveApiModel() : "");
        vars.put("modelProvider", context.getModelProvider() != null ? context.getModelProvider() : "");
        vars.put("prompt", request.getPrompt() != null ? request.getPrompt() : "");
        vars.put("systemPrompt", request.getSystemPrompt() != null ? request.getSystemPrompt() : "");
        vars.put("text", request.getText() != null ? request.getText() : "");
        vars.put("referenceImageUrl", request.getReferenceImageUrl() != null ? request.getReferenceImageUrl() : "");
        vars.put("voiceMaterialCode", request.getVoiceMaterialCode() != null ? request.getVoiceMaterialCode() : "");
        vars.put("nodeKey", request.getNodeKey() != null ? request.getNodeKey() : "");
        // extra 参数
        if (request.getExtra() != null) {
            for (Map.Entry<String, Object> e : request.getExtra().entrySet()) {
                vars.put("extra." + e.getKey(), e.getValue() != null ? String.valueOf(e.getValue()) : "");
            }
        }
        return vars;
    }

    /**
     * 填充模板中的 {{variable}} 占位符。
     */
    private String fillTemplate(String template, Map<String, String> variables) {
        if (template == null || template.isEmpty()) {
            return "";
        }
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String varName = matcher.group(1);
            String value = variables.getOrDefault(varName, "");
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * 发送 HTTP 请求。
     */
    private HttpResponse<String> sendRequest(AiModelContext context, CustomHttpTemplate template,
                                             Map<String, String> variables) throws Exception {
        CustomHttpTemplate.HttpRequestConfig reqConfig = template.getRequest();
        String url = context.getApiUrl() + (reqConfig.getPath() != null ? reqConfig.getPath() : "");
        String method = reqConfig.getMethod() != null ? reqConfig.getMethod().toUpperCase() : "POST";
        String body = fillTemplate(reqConfig.getBody(), variables);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
                .build();

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS));

        // 填充 headers
        if (reqConfig.getHeaders() != null) {
            for (Map.Entry<String, String> entry : reqConfig.getHeaders().entrySet()) {
                builder.header(entry.getKey(), fillTemplate(entry.getValue(), variables));
            }
        }

        // 按 method 设置 body
        if ("GET".equals(method)) {
            builder.GET();
        } else if ("POST".equals(method)) {
            builder.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        } else if ("PUT".equals(method)) {
            builder.PUT(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        } else if ("DELETE".equals(method)) {
            if (!body.isEmpty()) {
                builder.method("DELETE", HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            } else {
                builder.DELETE();
            }
        } else {
            builder.method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        }

        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /**
     * 解析成功响应，按模板配置提取 text 或 resourceUrl。
     */
    private AiInvokeResponse parseSuccessResponse(CustomHttpTemplate template, String responseBody, long costMs) {
        CustomHttpTemplate.HttpResponseConfig respConfig = template.getResponse();
        String rawPreview = truncate(responseBody, 500);

        try {
            JsonNode root = responseBody != null && !responseBody.isEmpty()
                    ? objectMapper.readTree(responseBody) : null;

            // 提取文本内容（文本模型）
            String text = null;
            if (respConfig.getTextJsonPath() != null && !respConfig.getTextJsonPath().isEmpty() && root != null) {
                text = extractJsonPath(root, respConfig.getTextJsonPath());
            }

            // 提取资源 URL（图像/音频/视频模型）
            String resourceUrl = null;
            if (respConfig.getResourceUrlJsonPath() != null && !respConfig.getResourceUrlJsonPath().isEmpty() && root != null) {
                resourceUrl = extractJsonPath(root, respConfig.getResourceUrlJsonPath());
            }

            return AiInvokeResponse.builder()
                    .success(true)
                    .text(text)
                    .resourceUrl(resourceUrl)
                    .resourceType(respConfig.getResourceType())
                    .costMs(costMs)
                    .rawResponse(rawPreview)
                    .build();
        } catch (Exception e) {
            log.warn("CustomHttpInvoker 响应解析失败：template={}, error={}", template.getName(), e.getMessage());
            return AiInvokeResponse.builder()
                    .success(true)
                    .costMs(costMs)
                    .rawResponse(rawPreview)
                    .extra(Map.of("parseWarning", e.getMessage()))
                    .build();
        }
    }

    /**
     * 提取错误信息。
     */
    private String extractError(CustomHttpTemplate template, String responseBody) {
        CustomHttpTemplate.HttpResponseConfig respConfig = template.getResponse();
        if (respConfig.getErrorJsonPath() == null || respConfig.getErrorJsonPath().isEmpty()
                || responseBody == null || responseBody.isEmpty()) {
            return truncate(responseBody, 200);
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String error = extractJsonPath(root, respConfig.getErrorJsonPath());
            return error != null ? error : truncate(responseBody, 200);
        } catch (Exception e) {
            return truncate(responseBody, 200);
        }
    }

    /**
     * 从 JSON 节点按路径提取值。
     * <p>
     * 支持点号分隔和数组索引，如 "choices[0].message.content"。
     */
    private String extractJsonPath(JsonNode root, String path) {
        String[] segments = path.split("\\.");
        JsonNode current = root;
        for (String segment : segments) {
            if (current == null || current.isMissingNode()) {
                return null;
            }
            // 处理数组索引：segment 可能是 "choices[0]"
            int bracketStart = segment.indexOf('[');
            if (bracketStart >= 0) {
                String fieldName = segment.substring(0, bracketStart);
                if (!fieldName.isEmpty()) {
                    current = current.path(fieldName);
                }
                // 提取所有索引 [0], [1] 等
                Matcher idxMatcher = Pattern.compile("\\[(\\d+)]").matcher(segment.substring(bracketStart));
                while (idxMatcher.find() && current != null && !current.isMissingNode()) {
                    int idx = Integer.parseInt(idxMatcher.group(1));
                    current = current.path(idx);
                }
            } else {
                current = current.path(segment);
            }
        }
        if (current == null || current.isMissingNode() || current.isNull()) {
            return null;
        }
        return current.isTextual() ? current.asText() : current.toString();
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return null;
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }
}
