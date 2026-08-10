package com.comicdrama.workflow.handler;

import com.comicdrama.common.ai.AiModelContext;

import java.util.List;

/**
 * AI 模型配置提供者。
 * 根据模型服务商获取 {@link AiModelContext}（API 地址、密钥、超时等）。
 * Phase-2 实现：从 ai_model_config 表加载（通过 Feign 或本地 Service）。
 */
public interface AiModelConfigProvider {

    /**
     * 按模型服务商获取配置（兼容旧接口，返回第一个匹配的模型）。
     *
     * @param modelProvider 模型服务商（如 deepseek / modelscope / seedream）
     * @return 模型调用上下文
     */
    AiModelContext getByProvider(String modelProvider);

    /**
     * 按模型服务商 + 模型类型获取配置（精确匹配）。
     * 当同一服务商下有多种模型时（如 modelscope 同时提供文本和图像模型），
     * 必须通过此方法区分。
     *
     * @param modelProvider 模型服务商
     * @param modelType     模型类型（1=文本, 2=图像, 3=语音, 4=视频）
     * @return 模型调用上下文
     */
    AiModelContext getByProviderAndType(String modelProvider, Integer modelType);

    /**
     * 按模型类型获取所有启用的模型候选列表（用于负载均衡）。
     * <p>
     * 当同一类型下配置了多个启用模型时，调用方通过 {@link com.comicdrama.workflow.ai.selector.ModelSelectorFactory}
     * 按策略选择一个。
     *
     * @param modelType 模型类型
     * @return 所有匹配的启用模型列表，无匹配时返回空列表
     */
    List<AiModelContext> listByType(Integer modelType);
}