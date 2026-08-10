package com.comicdrama.common.enums;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;

/**
 * AI 模型负载均衡策略枚举。
 * 同一模型类型下配置多个启用模型时，按策略选择一个进行调用。
 *
 * @see com.comicdrama.common.ai.AiModelContext#getSelectorStrategy()
 */
@Getter
@AllArgsConstructor
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum SelectorStrategy {

    WEIGHTED_RANDOM("WEIGHTED_RANDOM", "加权随机", "按 weight 字段加权随机选择，权重越大被选中概率越高（默认）"),
    ROUND_ROBIN("ROUND_ROBIN", "轮询", "按配置顺序依次轮询，请求均匀分布到各模型"),
    LOWEST_COST("LOWEST_COST", "最低成本优先", "优先选择单位 Token 成本最低的模型（需配合成本配置）"),
    FASTEST_RESPONSE("FASTEST_RESPONSE", "最快响应优先", "优先选择历史平均响应耗时最短的模型");

    /** 策略标识（存入 ai_model_config.selector_strategy 字段） */
    private final String code;
    /** 策略中文名 */
    private final String desc;
    /** 说明 */
    private final String description;

    /**
     * 根据策略标识查找枚举，未知值返回 null。
     */
    public static SelectorStrategy fromCode(String code) {
        if (code == null || code.isEmpty()) {
            return null;
        }
        for (SelectorStrategy strategy : values()) {
            if (strategy.code.equals(code)) {
                return strategy;
            }
        }
        return null;
    }

    /**
     * 判断策略标识是否合法。
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }

    /**
     * 返回所有可选策略（用于前端下拉）。
     */
    public static List<SelectorStrategy> allStrategies() {
        return Arrays.asList(values());
    }
}
