package com.comicdrama.workflow.ai;

import com.comicdrama.common.ai.AiModelInvoker;
import com.comicdrama.common.enums.ModelType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 模型 Invoker 注册表。
 * 启动时扫描所有 {@link AiModelInvoker} Bean，按 protocol+type 构建主索引，运行时 O(1) 路由。
 *
 * 路由优先级：
 * 1. {@link #get(String, ModelType)} — 按 protocol+type 精确匹配（新路径）
 * 2. {@link #getByTypeAndProvider(ModelType, String)} — 按 type+provider 走旧 supports 逻辑（fallback）
 * 3. {@link #getByType(ModelType)} — 按 type 取第一个（最终兜底）
 */
@Slf4j
@Component
public class InvokerRegistry {

    /** 主索引：protocol:type → invoker（O(1) 路由） */
    private final Map<String, AiModelInvoker> protocolIndex = new ConcurrentHashMap<>();

    /** 兜底索引：type → invoker 列表（仅当 protocol 未命中且配置无 protocol 时使用） */
    private final Map<ModelType, List<AiModelInvoker>> typeIndex = new ConcurrentHashMap<>();

    /** 所有已注册 Invoker */
    private final List<AiModelInvoker> allInvokers;

    public InvokerRegistry(List<AiModelInvoker> invokers) {
        this.allInvokers = invokers != null ? invokers : Collections.emptyList();

        for (AiModelInvoker invoker : this.allInvokers) {
            // 1. 注册到协议索引（新路径）
            for (String protocol : invoker.supportedProtocols()) {
                for (ModelType type : invoker.supportedModelTypes()) {
                    String key = buildKey(protocol, type);
                    AiModelInvoker prev = protocolIndex.putIfAbsent(key, invoker);
                    if (prev != null && prev != invoker) {
                        log.warn("协议[{}]类型[{}]已被 Invoker[{}]注册，当前[{}]将作为备份",
                                protocol, type, prev.getClass().getSimpleName(),
                                invoker.getClass().getSimpleName());
                    }
                }
            }
            // 2. 注册到类型索引（fallback 用）
            registerToTypeIndex(invoker);
        }

        log.info("InvokerRegistry 初始化完成：共 {} 个 Invoker，protocolIndex={}, typeIndex={}",
                this.allInvokers.size(), protocolIndex.keySet(), typeIndex.keySet());
    }

    /**
     * 注册到类型索引。优先用 supportedModelTypes()，未实现时 fallback 到遍历 supports(ModelType)。
     */
    private void registerToTypeIndex(AiModelInvoker invoker) {
        Set<ModelType> types = invoker.supportedModelTypes();
        if (types != null && !types.isEmpty()) {
            for (ModelType type : types) {
                typeIndex.computeIfAbsent(type, k -> new ArrayList<>()).add(invoker);
            }
        } else {
            // 兼容：supportedModelTypes 未实现时回退到 supports(ModelType)
            for (ModelType t : ModelType.values()) {
                if (invoker.supports(t)) {
                    typeIndex.computeIfAbsent(t, k -> new ArrayList<>()).add(invoker);
                }
            }
        }
    }

    /**
     * 主路由：按 protocol+type 精确查找（新路径）。
     *
     * @param protocol 协议标识（如 "openai-chat"），为空返回 null
     * @param type     模型类型
     * @return 匹配的 Invoker，未找到返回 null
     */
    public AiModelInvoker get(String protocol, ModelType type) {
        if (protocol == null || protocol.isEmpty()) {
            return null;
        }
        return protocolIndex.get(buildKey(protocol, type));
    }

    /**
     * Fallback 路由：按 type 查找所有候选 Invoker。
     */
    public List<AiModelInvoker> getByType(ModelType type) {
        return typeIndex.getOrDefault(type, Collections.emptyList());
    }

    /**
     * Fallback 路由：按 type+provider 走旧 supports 逻辑。
     * 先匹配 supports(type)+supports(provider)，再取 type 下第一个。
     */
    public AiModelInvoker getByTypeAndProvider(ModelType type, String provider) {
        List<AiModelInvoker> list = getByType(type);
        if (list.isEmpty()) {
            return null;
        }
        // 优先匹配 provider
        if (provider != null && !provider.isEmpty()) {
            for (AiModelInvoker inv : list) {
                if (inv.supports(provider)) {
                    return inv;
                }
            }
        }
        // 兜底：返回该类型第一个
        return list.get(0);
    }

    /**
     * 返回所有已注册 Invoker（用于调试/连通性测试）。
     */
    public List<AiModelInvoker> all() {
        return allInvokers;
    }

    private static String buildKey(String protocol, ModelType type) {
        return protocol + ":" + type.getCode();
    }
}
