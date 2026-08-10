package com.comicdrama.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.comicdrama.common.dto.PageQuery;
import com.comicdrama.common.enums.ModelProtocol;
import com.comicdrama.common.exception.BizException;
import com.comicdrama.common.result.PageResult;
import com.comicdrama.common.result.ResultCode;
import com.comicdrama.system.dto.AiModelTestResultDTO;
import com.comicdrama.system.entity.AiModelConfig;
import com.comicdrama.system.mapper.AiModelConfigMapper;
import com.comicdrama.system.mapper.StepModelBindingMapper;
import com.comicdrama.system.service.AiModelConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiModelConfigServiceImpl extends ServiceImpl<AiModelConfigMapper, AiModelConfig> implements AiModelConfigService {

    private final StepModelBindingMapper bindingMapper;
    private final ObjectMapper objectMapper;

    /** 连通性测试超时时间（秒） */
    private static final int TEST_TIMEOUT_SECONDS = 15;

    /** 连通性测试最大响应体截断长度 */
    private static final int MAX_RESPONSE_PREVIEW = 300;

    @Override
    public PageResult<AiModelConfig> page(PageQuery query, String keyword, Integer modelType) {
        LambdaQueryWrapper<AiModelConfig> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(AiModelConfig::getModelProvider, keyword)
                    .or().like(AiModelConfig::getModelName, keyword);
        }
        if (modelType != null) {
            wrapper.eq(AiModelConfig::getModelType, modelType);
        }
        wrapper.orderByAsc(AiModelConfig::getModelType)
                .orderByAsc(AiModelConfig::getModelProvider);
        Page<AiModelConfig> page = new Page<>(query.getPage(), query.getSize());
        Page<AiModelConfig> result = this.page(page, wrapper);
        result.getRecords().forEach(this::maskSecret);
        return new PageResult<>(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public AiModelConfig getMaskedById(Long id) {
        AiModelConfig config = this.getById(id);
        if (config == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND);
        }
        maskSecret(config);
        return config;
    }

    @Override
    public List<AiModelConfig> listEnabled() {
        List<AiModelConfig> list = this.list(new LambdaQueryWrapper<AiModelConfig>()
                .eq(AiModelConfig::getStatus, 1)
                .orderByAsc(AiModelConfig::getModelType)
                .orderByAsc(AiModelConfig::getModelProvider));
        list.forEach(this::maskSecret);
        return list;
    }

    @Override
    @Transactional
    public void toggleStatus(Long id, Integer status) {
        AiModelConfig config = this.getById(id);
        if (config == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND);
        }
        AiModelConfig update = new AiModelConfig();
        update.setId(id);
        update.setStatus(status);
        this.updateById(update);

        // 禁用时清理所有引用此模型的绑定
        if (status != null && status == 0) {
            int cleared = bindingMapper.clearBindingByModelConfigId(id);
            if (cleared > 0) {
                log.info("禁用模型 id={}，已清除 {} 条步骤-模型绑定", id, cleared);
            }
        }
    }

    /**
     * 重写 removeById：删除模型前清理绑定引用。
     */
    @Override
    @Transactional
    public boolean removeById(AiModelConfig entity) {
        if (entity != null && entity.getId() != null) {
            int cleared = bindingMapper.clearBindingByModelConfigId(entity.getId());
            if (cleared > 0) {
                log.info("删除模型 id={}，已清除 {} 条步骤-模型绑定", entity.getId(), cleared);
            }
        }
        return super.removeById(entity);
    }

    /**
     * 重写 removeById（按 ID 删除）：删除模型前清理绑定引用。
     */
    @Override
    @Transactional
    public boolean removeById(java.io.Serializable id) {
        if (id != null) {
            int cleared = bindingMapper.clearBindingByModelConfigId((Long) id);
            if (cleared > 0) {
                log.info("删除模型 id={}，已清除 {} 条步骤-模型绑定", id, cleared);
            }
        }
        return super.removeById(id);
    }

    /**
     * 重写 updateById：状态变更为禁用时清理绑定引用。
     */
    @Override
    @Transactional
    public boolean updateById(AiModelConfig entity) {
        if (entity != null && entity.getId() != null && entity.getStatus() != null && entity.getStatus() == 0) {
            AiModelConfig existing = this.getById(entity.getId());
            if (existing != null && existing.getStatus() != null && existing.getStatus() == 1) {
                int cleared = bindingMapper.clearBindingByModelConfigId(entity.getId());
                if (cleared > 0) {
                    log.info("禁用模型 id={}，已清除 {} 条步骤-模型绑定", entity.getId(), cleared);
                }
            }
        }
        return super.updateById(entity);
    }

    // ==================== 连通性测试 ====================

    /**
     * 连通性测试：按模型配置的协议发起最小化探测请求。
     * <p>
     * 探测策略按协议区分：
     * <ul>
     *   <li>openai-chat：发送 max_tokens=1 的最小 chat 请求，验证密钥与模型可用性</li>
     *   <li>其余协议：对配置的 apiUrl 发送带鉴权头的轻量请求，按 HTTP 状态码判定连通性</li>
     * </ul>
     * 注意：此处仅做轻量探测，不调用真实 Invoker（system-service 不依赖 workflow-service）。
     */
    @Override
    public AiModelTestResultDTO testConnection(Long id) {
        AiModelConfig config = this.getById(id);
        if (config == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND);
        }

        String apiUrl = config.getApiUrl();
        if (!StringUtils.hasText(apiUrl)) {
            return AiModelTestResultDTO.fail(0, "API 地址未配置");
        }
        String apiKey = config.getApiKey();
        if (!StringUtils.hasText(apiKey)) {
            return AiModelTestResultDTO.fail(0, "API 密钥未配置");
        }

        String protocol = config.getProtocol();
        ModelProtocol protocolEnum = ModelProtocol.fromCode(protocol);
        log.info("开始连通性测试：id={}, provider={}, model={}, protocol={}, url={}",
                id, config.getModelProvider(), config.getModelName(), protocol, apiUrl);

        long start = System.currentTimeMillis();
        try {
            AiModelTestResultDTO result;
            if (protocolEnum == ModelProtocol.OPENAI_CHAT) {
                result = testOpenAiChat(config);
            } else {
                result = testGenericHttp(config);
            }
            log.info("连通性测试完成：id={}, success={}, latency={}ms, message={}",
                    id, result.getSuccess(), result.getLatencyMs(), result.getMessage());
            return result;
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - start;
            log.warn("连通性测试异常：id={}, error={}", id, e.getMessage());
            return AiModelTestResultDTO.fail(cost, "测试请求异常：" + e.getMessage());
        }
    }

    /**
     * OpenAI 兼容对话协议探测：发送 max_tokens=1 的最小请求。
     * 200/400（参数类）均视为"地址可达 + 密钥有效"，401/403 视为密钥无效。
     */
    private AiModelTestResultDTO testOpenAiChat(AiModelConfig config) throws Exception {
        String url = stripTrailingSlash(config.getApiUrl()) + "/chat/completions";
        String modelName = StringUtils.hasText(config.getModelName())
                ? config.getModelName() : config.getModelProvider();

        Map<String, Object> body = new HashMap<>();
        body.put("model", modelName);
        body.put("messages", List.of(Map.of("role", "user", "content", "ping")));
        body.put("max_tokens", 1);
        body.put("stream", false);
        String jsonBody = objectMapper.writeValueAsString(body);

        long start = System.currentTimeMillis();
        HttpResponse<String> response = sendJsonRequest(url, config.getApiKey(), jsonBody);
        long cost = System.currentTimeMillis() - start;
        int status = response.statusCode();
        String preview = truncate(response.body(), MAX_RESPONSE_PREVIEW);

        // 200：模型可用；400：通常为模型名错误，但密钥有效，视为连通成功
        if (status >= 200 && status < 300) {
            return AiModelTestResultDTO.ok(status, cost, "连通成功，模型响应正常。" + preview);
        }
        if (status == 400) {
            return AiModelTestResultDTO.ok(status, cost,
                    "地址可达、密钥有效，但模型名可能不正确（HTTP 400）。" + preview);
        }
        if (status == 401 || status == 403) {
            return AiModelTestResultDTO.fail(status, cost, "密钥无效或无访问权限（HTTP " + status + "）。" + preview);
        }
        if (status == 429) {
            return AiModelTestResultDTO.fail(status, cost, "配额已耗尽或限流（HTTP 429）。" + preview);
        }
        return AiModelTestResultDTO.fail(status, cost, "服务端返回 HTTP " + status + "。" + preview);
    }

    /**
     * 通用 HTTP 探测：对配置地址发送带鉴权头的 HEAD/GET 请求。
     * 由于多数 AI 服务仅支持 POST，此处采用 POST 空 JSON 的方式探测，以状态码判定连通性。
     */
    private AiModelTestResultDTO testGenericHttp(AiModelConfig config) throws Exception {
        String url = config.getApiUrl();
        long start = System.currentTimeMillis();
        HttpResponse<String> response = sendJsonRequest(url, config.getApiKey(), "{}");
        long cost = System.currentTimeMillis() - start;
        int status = response.statusCode();
        String preview = truncate(response.body(), MAX_RESPONSE_PREVIEW);

        // 2xx：连通成功
        // 4xx（除 401/403/429）：地址可达，密钥可能有效，仅参数不匹配
        if (status >= 200 && status < 300) {
            return AiModelTestResultDTO.ok(status, cost, "连通成功。" + preview);
        }
        if (status == 401 || status == 403) {
            return AiModelTestResultDTO.fail(status, cost, "密钥无效或无访问权限（HTTP " + status + "）。" + preview);
        }
        if (status == 429) {
            return AiModelTestResultDTO.fail(status, cost, "配额已耗尽或限流（HTTP 429）。" + preview);
        }
        if (status >= 400 && status < 500) {
            return AiModelTestResultDTO.ok(status, cost,
                    "地址可达、密钥可能有效（HTTP " + status + "，需用真实业务请求进一步验证）。" + preview);
        }
        return AiModelTestResultDTO.fail(status, cost, "服务端返回 HTTP " + status + "。" + preview);
    }

    /**
     * 发送 JSON POST 请求，返回完整响应。
     */
    private HttpResponse<String> sendJsonRequest(String url, String apiKey, String jsonBody) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(TEST_TIMEOUT_SECONDS))
                .build();

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(TEST_TIMEOUT_SECONDS))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8));

        if (StringUtils.hasText(apiKey)) {
            // OpenAI 兼容协议使用 Bearer；其他服务通常也接受 Authorization 头
            builder.header("Authorization", "Bearer " + apiKey);
        }

        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private String stripTrailingSlash(String url) {
        if (url == null) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private String truncate(String text, int maxLen) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }

    /** 密钥脱敏：仅保留前4后4，中间用 **** 代替；空则置 null */
    private void maskSecret(AiModelConfig c) {
        c.setApiKey(mask(c.getApiKey()));
    }

    private String mask(String secret) {
        if (!StringUtils.hasText(secret)) {
            return null;
        }
        if (secret.length() <= 8) {
            return "****";
        }
        return secret.substring(0, 4) + "****" + secret.substring(secret.length() - 4);
    }
}
