package com.comicdrama.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.comicdrama.common.entity.BaseCreateTimeEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("token_usage_log")
public class TokenUsageLog extends BaseCreateTimeEntity {
    private Long taskId;
    private Long userId;
    private Integer step;
    private String nodeType;
    private String modelName;
    private Integer modelType;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private Integer imageCount;
    private BigDecimal videoDuration;
    private Integer latencyMs;
    private Integer status;
    private String errorMsg;
    private String inputContent;
    private String outputContent;
}