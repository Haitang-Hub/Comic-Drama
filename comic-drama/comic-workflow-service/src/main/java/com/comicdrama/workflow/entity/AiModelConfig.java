package com.comicdrama.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.comicdrama.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_model_config")
public class AiModelConfig extends BaseEntity {
    private String modelProvider;
    private String modelName;
    private Integer modelType;
    /** 调用协议标识（openai-chat/modelscope-image/ark-image/ark-tts/ark-video/custom-http-*） */
    private String protocol;
    /** 能力声明 JSON 字符串，如 ["STREAMING","IMAGE_TO_IMAGE"] */
    private String capabilities;
    /** 负载均衡策略：WEIGHTED_RANDOM/ROUND_ROBIN/LOWEST_COST/FASTEST_RESPONSE */
    private String selectorStrategy;
    private String apiUrl;
    private String apiKey;
    private Integer status;
    private Integer weight;
}
