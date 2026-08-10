package com.comicdrama.workflow.ai.selector;

import com.comicdrama.common.ai.AiModelContext;
import com.comicdrama.common.enums.SelectorStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 模型负载均衡选择器工厂。
 * <p>
 * 按 {@link AiModelContext#getSelectorStrategy()} 返回对应的 {@link ModelSelector} 实现。
 * 内置 4 种策略：
 * <ul>
 *   <li>{@link WeightedRandomSelector} — 加权随机（默认）</li>
 *   <li>{@link RoundRobinSelector} — 轮询</li>
 *   <li>{@link LowestCostSelector} — 最低成本优先</li>
 *   <li>{@link FastestResponseSelector} — 最快响应优先</li>
 * </ul>
 */
@Slf4j
@Component
public class ModelSelectorFactory {

    private final Map<String, ModelSelector> selectors = new ConcurrentHashMap<>();

    public ModelSelectorFactory() {
        register(new WeightedRandomSelector());
        register(new RoundRobinSelector());
        register(new LowestCostSelector());
        register(new FastestResponseSelector());
    }

    private void register(ModelSelector selector) {
        selectors.put(selector.strategy(), selector);
    }

    /**
     * 按策略标识获取选择器，未知策略回退到加权随机。
     */
    public ModelSelector get(String strategy) {
        if (strategy == null || strategy.isEmpty()) {
            return selectors.get(SelectorStrategy.WEIGHTED_RANDOM.getCode());
        }
        return selectors.getOrDefault(strategy, selectors.get(SelectorStrategy.WEIGHTED_RANDOM.getCode()));
    }

    /**
     * 便捷方法：直接按策略选择模型。
     */
    public AiModelContext select(String strategy, List<AiModelContext> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        if (candidates.size() == 1) {
            return candidates.get(0);
        }
        ModelSelector selector = get(strategy);
        AiModelContext selected = selector.select(candidates);
        log.debug("负载均衡选择：strategy={}, candidates={}, selected={}",
                strategy, candidates.size(),
                selected != null ? selected.getModelName() : "null");
        return selected;
    }

    // ==================== 策略实现 ====================

    /**
     * 加权随机：按 weight 字段加权随机选择。
     */
    static class WeightedRandomSelector implements ModelSelector {
        @Override
        public AiModelContext select(List<AiModelContext> candidates) {
            int totalWeight = 0;
            for (AiModelContext c : candidates) {
                totalWeight += (c.getWeight() != null && c.getWeight() > 0) ? c.getWeight() : 100;
            }
            int random = ThreadLocalRandom.current().nextInt(totalWeight);
            int cumulative = 0;
            for (AiModelContext c : candidates) {
                int w = (c.getWeight() != null && c.getWeight() > 0) ? c.getWeight() : 100;
                cumulative += w;
                if (random < cumulative) {
                    return c;
                }
            }
            return candidates.get(candidates.size() - 1);
        }

        @Override
        public String strategy() {
            return SelectorStrategy.WEIGHTED_RANDOM.getCode();
        }
    }

    /**
     * 轮询：按配置顺序依次轮询，请求均匀分布。
     */
    static class RoundRobinSelector implements ModelSelector {
        private final AtomicInteger counter = new AtomicInteger(0);

        @Override
        public AiModelContext select(List<AiModelContext> candidates) {
            int idx = Math.abs(counter.getAndIncrement()) % candidates.size();
            return candidates.get(idx);
        }

        @Override
        public String strategy() {
            return SelectorStrategy.ROUND_ROBIN.getCode();
        }
    }

    /**
     * 最低成本优先：按 weight 倒序排列（weight 越小成本越低的约定），取第一个。
     * <p>
     * 注：真实成本需要对接 pricing 表，此处简化用 weight 反向排序作为示意。
     */
    static class LowestCostSelector implements ModelSelector {
        @Override
        public AiModelContext select(List<AiModelContext> candidates) {
            return candidates.stream()
                    .min(Comparator.comparingInt(c ->
                            (c.getWeight() != null && c.getWeight() > 0) ? c.getWeight() : 100))
                    .orElse(candidates.get(0));
        }

        @Override
        public String strategy() {
            return SelectorStrategy.LOWEST_COST.getCode();
        }
    }

    /**
     * 最快响应优先：优先选择历史平均响应耗时最短的模型。
     * <p>
     * 注：真实耗时需要对接 token_usage_log 表统计，此处简化为随机选择（后续可扩展）。
     */
    static class FastestResponseSelector implements ModelSelector {
        @Override
        public AiModelContext select(List<AiModelContext> candidates) {
            // TODO: 对接 token_usage_log 统计历史平均 latencyMs，选最快模型
            // 当前简化实现：随机选择，避免全部流量集中到一个模型
            return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        }

        @Override
        public String strategy() {
            return SelectorStrategy.FASTEST_RESPONSE.getCode();
        }
    }
}
