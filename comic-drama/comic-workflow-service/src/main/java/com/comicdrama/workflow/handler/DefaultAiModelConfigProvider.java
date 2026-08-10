package com.comicdrama.workflow.handler;

import com.comicdrama.common.ai.AiModelContext;
import com.comicdrama.workflow.entity.AiModelConfig;
import com.comicdrama.workflow.mapper.AiModelConfigMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 模型配置提供者默认实现。
 * 从数据库 ai_model_config 表加载真实配置，启动时全量加载到缓存。
 * 若数据库无配置，回退到内置默认配置（保证系统可启动）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultAiModelConfigProvider implements AiModelConfigProvider {

    private final AiModelConfigMapper aiModelConfigMapper;
    // 复合 key: modelProvider + ":" + modelType + ":" + modelName
    // 确保同一服务商下同类型的不同模型（如 modelscope 下的多个图像模型）不会互相覆盖
    private final Map<String, AiModelContext> cache = new ConcurrentHashMap<>();

    /**
     * 生成复合缓存 key（provider:type:modelName）。
     */
    private static String buildKey(String provider, Integer modelType, String modelName) {
        return provider + ":" + modelType + ":" + modelName;
    }

    @PostConstruct
    public void init() {
        try {
            reload();
        } catch (Exception e) {
            log.error("从数据库加载 AI 模型配置失败，使用内置默认配置", e);
            initDefaults();
        }
    }

    /**
     * 从数据库重新加载所有启用的模型配置。
     * 使用 provider:type:modelName 复合 key，确保同一服务商下同类型的不同模型不会互相覆盖。
     */
    public void reload() {
        cache.clear();
        LambdaQueryWrapper<AiModelConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiModelConfig::getStatus, 1);
        List<AiModelConfig> configs = aiModelConfigMapper.selectList(wrapper);

        if (configs == null || configs.isEmpty()) {
            log.warn("数据库 ai_model_config 表无启用的模型配置，回退到内置默认配置");
            initDefaults();
            return;
        }

        for (AiModelConfig c : configs) {
            AiModelContext ctx = AiModelContext.builder()
                    .id(c.getId())
                    .modelProvider(c.getModelProvider())
                    .modelName(c.getModelName())
                    .modelType(c.getModelType())
                    .protocol(c.getProtocol())
                    .capabilities(com.comicdrama.common.enums.ModelCapability.parseSet(c.getCapabilities()))
                    .selectorStrategy(c.getSelectorStrategy())
                    .weight(c.getWeight())
                    .apiUrl(c.getApiUrl())
                    .apiKey(c.getApiKey())
                    .status(c.getStatus())
                    .build();
            String key = buildKey(c.getModelProvider(), c.getModelType(), c.getModelName());
            cache.put(key, ctx);
            log.info("加载模型: key={}, provider={}, name={}, type={}, protocol={}",
                    key, c.getModelProvider(), c.getModelName(), c.getModelType(), c.getProtocol());
        }
        log.info("从数据库加载 AI 模型配置 {} 条（复合 key）：{}", cache.size(), cache.keySet());
    }

    /**
     * 不使用内置默认配置（模型配置只从数据库加载）。
     */
    private void initDefaults() {
        if (!cache.isEmpty()) {
            return;
        }
    }

    @Override
    public AiModelContext getByProvider(String modelProvider) {
        if (modelProvider == null || modelProvider.isEmpty()) {
            throw new IllegalArgumentException("模型服务商不能为空");
        }
        // 遍历缓存查找第一个匹配 provider 的模型
        for (AiModelContext ctx : cache.values()) {
            if (ctx.getModelProvider().equals(modelProvider)) {
                return ctx;
            }
        }
        log.warn("模型服务商[{}]不在缓存中，尝试从数据库重新加载", modelProvider);
        try {
            reload();
            for (AiModelContext ctx : cache.values()) {
                if (ctx.getModelProvider().equals(modelProvider)) {
                    return ctx;
                }
            }
        } catch (Exception e) {
            log.error("重新加载模型配置失败", e);
        }
        throw new IllegalArgumentException(
                "AI 模型配置不存在：" + modelProvider + "，当前已加载模型：" + cache.keySet());
    }

    @Override
    public AiModelContext getByProviderAndType(String modelProvider, Integer modelType) {
        if (modelProvider == null || modelProvider.isEmpty()) {
            throw new IllegalArgumentException("模型服务商不能为空");
        }
        // 遍历缓存查找第一个匹配 provider + type 的模型
        for (AiModelContext ctx : cache.values()) {
            if (ctx.getModelProvider().equals(modelProvider) && ctx.getModelType().equals(modelType)) {
                return ctx;
            }
        }
        log.warn("模型[{}:{}]不在缓存中，尝试从数据库重新加载", modelProvider, modelType);
        try {
            reload();
            for (AiModelContext ctx : cache.values()) {
                if (ctx.getModelProvider().equals(modelProvider) && ctx.getModelType().equals(modelType)) {
                    return ctx;
                }
            }
        } catch (Exception e) {
            log.error("重新加载模型配置失败", e);
        }
        throw new IllegalArgumentException(
                "AI 模型配置不存在：provider=" + modelProvider + ", type=" + modelType
                        + "，当前已加载模型：" + cache.keySet());
    }

    public List<AiModelContext> listAll() {
        return List.copyOf(cache.values());
    }

    /**
     * 按模型类型获取所有启用的模型候选列表（用于负载均衡）。
     */
    @Override
    public List<AiModelContext> listByType(Integer modelType) {
        if (modelType == null) {
            return java.util.Collections.emptyList();
        }
        return cache.values().stream()
                .filter(ctx -> modelType.equals(ctx.getModelType()))
                .toList();
    }
}
