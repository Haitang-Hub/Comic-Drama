package com.comicdrama.common.ai;

import com.comicdrama.common.enums.ModelCapability;
import com.comicdrama.common.enums.ModelType;

import java.util.Collections;
import java.util.Set;

/**
 * AI 模型调用抽象层。
 * 协议化改造后，路由优先基于 {@link #supportedProtocols()} + {@link #supportedModelTypes()}
 * 通过 InvokerRegistry O(1) 精确匹配，旧 {@link #supports(ModelType)} / {@link #supports(String)}
 * 保留作为 fallback（兼容无 protocol 字段的旧数据）。
 *
 * 新增 OpenAI 兼容服务商只需在 ai_model_config 表配置 protocol=openai-chat，无需写新 Invoker 类。
 */
public interface AiModelInvoker {

    /** 调用模型 */
    AiInvokeResponse invoke(AiModelContext context, AiInvokeRequest request);

    // ==================== 旧路由接口（fallback 用，新代码请用 supportedProtocols/supportedModelTypes） ====================

    /** 是否支持该模型类型（旧接口，保留用于 fallback） */
    boolean supports(ModelType modelType);

    /** 是否支持该模型服务商（旧接口，保留用于 fallback） */
    default boolean supports(String modelProvider) {
        return false;
    }

    // ==================== 新协议路由接口 ====================

    /**
     * 声明支持的协议标识集合（如 Set.of("openai-chat")）。
     * InvokerRegistry 启动时按 protocol+type 构建索引，运行时 O(1) 路由。
     * 未实现时返回空集，注册表将 fallback 到旧 supports 逻辑。
     */
    default Set<String> supportedProtocols() {
        return Collections.emptySet();
    }

    /**
     * 声明支持的模型类型集合（替代旧 supports(ModelType)，便于注册表索引）。
     * 未实现时返回空集，注册表将 fallback 到遍历 supports(ModelType)。
     */
    default Set<ModelType> supportedModelTypes() {
        return Collections.emptySet();
    }

    /**
     * 声明该 Invoker 提供的能力（静态基线，可被 ai_model_config.capabilities 配置补充）。
     * 步骤 Handler 通过 modelSupports(capability) 声明式查询，决定是否走某条路径。
     */
    default Set<ModelCapability> capabilities() {
        return Collections.emptySet();
    }
}
