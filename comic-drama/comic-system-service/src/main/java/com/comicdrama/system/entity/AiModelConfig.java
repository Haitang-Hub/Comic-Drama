package com.comicdrama.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.comicdrama.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI模型配置表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_model_config")
public class AiModelConfig extends BaseEntity {

    /** 模型服务商（任意自定义值，与 AiModelInvoker.supports() 方法匹配） */
    private String modelProvider;

    private String modelName;

    /** 模型类型：1文本生成 2图片生成 3音频生成 4视频生成 */
    private Integer modelType;

    /** 调用协议：openai-chat/modelscope-image/ark-image/ark-tts/ark-video/custom-http-*，NULL时按旧supports逻辑路由 */
    private String protocol;

    /** 能力声明 JSON 数组字符串，如 ["STREAMING","IMAGE_TO_IMAGE"] */
    private String capabilities;

    /** 负载均衡策略：WEIGHTED_RANDOM/ROUND_ROBIN/LOWEST_COST/FASTEST_RESPONSE */
    private String selectorStrategy;

    private String apiUrl;

    /** API密钥（加密存储，对外脱敏） */
    private String apiKey;

    /** 状态：0禁用 1启用 */
    private Integer status;

    /** 权重（多模型负载均衡，值越大调度概率越高，默认100） */
    private Integer weight;
}
