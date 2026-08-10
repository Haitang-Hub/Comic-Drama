package com.comicdrama.workflow.ai.selector;

import com.comicdrama.common.ai.AiModelContext;

import java.util.List;

/**
 * AI 模型负载均衡选择器。
 * <p>
 * 当同一模型类型下配置了多个启用模型时，按策略选择一个进行调用。
 * 策略由 {@link com.comicdrama.common.enums.SelectorStrategy} 定义，
 * 通过 {@link ModelSelectorFactory} 按配置的策略标识获取对应实现。
 */
public interface ModelSelector {

    /**
     * 从候选模型列表中选择一个。
     *
     * @param candidates 同一模型类型下的所有启用模型（至少 1 个）
     * @return 被选中的模型上下文，列表为空时返回 null
     */
    AiModelContext select(List<AiModelContext> candidates);

    /**
     * 返回此选择器对应的策略标识。
     */
    String strategy();
}
