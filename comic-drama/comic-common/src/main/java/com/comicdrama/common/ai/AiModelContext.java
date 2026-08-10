package com.comicdrama.common.ai;

import com.comicdrama.common.enums.ModelCapability;
import lombok.Builder;
import lombok.Data;

import java.util.Collections;
import java.util.Set;

/**
 * AI 模型调用上下文（来自 ai_model_config 表 + 系统全局参数）。
 */
@Data
@Builder
public class AiModelContext {

    private Long id;
    /** 模型服务商（如 modelscope/deepseek/seedream） */
    private String modelProvider;
    /** 模型名称（实际调用时的模型ID，如 deepseek-ai/DeepSeek-V3.2） */
    private String modelName;
    /** 1文本 2图像 3音频 4视频 */
    private Integer modelType;
    /** 调用协议标识（openai-chat/modelscope-image/ark-image/ark-tts/ark-video/custom-http-*），NULL时走旧supports逻辑 */
    private String protocol;
    /** 能力声明集合（来自 ai_model_config.capabilities JSON 字段解析） */
    @Builder.Default
    private Set<ModelCapability> capabilities = Collections.emptySet();
    /** 负载均衡策略：WEIGHTED_RANDOM/ROUND_ROBIN/LOWEST_COST/FASTEST_RESPONSE */
    @Builder.Default
    private String selectorStrategy = "WEIGHTED_RANDOM";
    /** 权重（多模型负载均衡，值越大调度概率越高） */
    @Builder.Default
    private Integer weight = 100;
    private String apiUrl;
    private String apiKey;
    private String secretKey;
    private Integer status;

    /**
     * 解析实际发送给 API 的模型名称。
     * 使用 modelName（数据库配置的真实模型标识），为空时回退到 modelProvider。
     */
    public String resolveApiModel() {
        return (modelName != null && !modelName.isEmpty()) ? modelName : modelProvider;
    }

    /**
     * 判断模型是否具备某项能力（声明式能力查询）。
     * 能力来源：ai_model_config.capabilities 配置 + Invoker 静态 capabilities()，由调用方合并后传入。
     */
    public boolean hasCapability(ModelCapability capability) {
        return capabilities != null && capabilities.contains(capability);
    }
}
