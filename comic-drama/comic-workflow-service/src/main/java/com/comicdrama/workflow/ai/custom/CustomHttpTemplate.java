package com.comicdrama.workflow.ai.custom;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 自定义 HTTP 调用模板（YAML 驱动）。
 * <p>
 * 每个模板对应一种 API 接入方式，protocol 值为 {@code custom-http-{name}}。
 * 模板文件放在 {@code classpath:custom-http-templates/*.yml}，启动时由
 * {@link CustomHttpTemplateLoader} 加载。
 * <p>
 * 变量占位符语法：{@code {{variable}}}，运行时从 {@link com.comicdrama.common.ai.AiInvokeRequest}
 * 字段（prompt/systemPrompt/text/referenceImageUrl/voiceMaterialCode）和 extra 参数中填充。
 *
 * <h3>YAML 示例</h3>
 * <pre>
 * name: my-text-api
 * description: 接入自定义 OpenAI 兼容文本 API
 * modelTypes: [1]
 * capabilities: [STREAMING]
 * request:
 *   path: /v1/chat/completions
 *   method: POST
 *   headers:
 *     Content-Type: application/json
 *     Authorization: "Bearer {{apiKey}}"
 *   body: |
 *     {"model":"{{modelName}}","messages":[{"role":"user","content":"{{prompt}}"}]}
 * response:
 *   textJsonPath: choices[0].message.content
 *   resourceUrlJsonPath: data.url
 * </pre>
 */
@Data
public class CustomHttpTemplate {

    /** 模板名称（protocol 值为 custom-http-{name}） */
    private String name;

    /** 模板说明 */
    private String description;

    /** 支持的模型类型：1文本 2图像 3音频 4视频 */
    private List<Integer> modelTypes = new ArrayList<>();

    /** 静态能力声明（如 STREAMING / IMAGE_TO_IMAGE） */
    private List<String> capabilities = new ArrayList<>();

    /** 请求配置 */
    private HttpRequestConfig request = new HttpRequestConfig();

    /** 响应解析配置 */
    private HttpResponseConfig response = new HttpResponseConfig();

    @Data
    public static class HttpRequestConfig {
        /** 请求路径（拼接在 ai_model_config.apiUrl 之后） */
        private String path = "";
        /** HTTP 方法：GET/POST/PUT/DELETE，默认 POST */
        private String method = "POST";
        /** 请求头（支持变量占位符） */
        private Map<String, String> headers = new HashMap<>();
        /** 请求体模板（支持变量占位符），GET 请求可为空 */
        private String body = "";
    }

    @Data
    public static class HttpResponseConfig {
        /**
         * 从响应 JSON 中提取文本内容的字段路径。
         * 支持 Jackson JsonNode path 语法，如 "choices[0].message.content"。
         * 文本模型必填。
         */
        private String textJsonPath;

        /**
         * 从响应 JSON 中提取资源 URL 的字段路径。
         * 图像/音频/视频模型必填。
         */
        private String resourceUrlJsonPath;

        /**
         * 资源类型：image/audio/video（与 resourceUrlJsonPath 配合使用）。
         */
        private String resourceType;

        /**
         * 错误信息字段路径（可选，用于提取 API 返回的错误描述）。
         */
        private String errorJsonPath;
    }
}
