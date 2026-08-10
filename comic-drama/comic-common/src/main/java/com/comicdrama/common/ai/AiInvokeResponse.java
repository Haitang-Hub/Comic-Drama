package com.comicdrama.common.ai;

import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * AI 调用统一响应（Phase-1 仅声明，Phase-2 由各 Invoker 填充）。
 */
@Data
@Builder
public class AiInvokeResponse {

    /** 是否成功 */
    private boolean success;

    /** 生成文本（文本模型） */
    private String text;

    /** 生成资源 URL（图像/音频/视频，已存入存储后的可访问 URL） */
    private String resourceUrl;

    /** 资源类型 image/audio/video */
    private String resourceType;

    /** 耗时（毫秒） */
    private Long costMs;

    /** 模型原始返回（调试用） */
    private String rawResponse;

    /** 错误信息（success=false 时） */
    private String errorMessage;

    /** 附加结果（如宽高、时长等） */
    @Builder.Default
    private Map<String, Object> extra = new HashMap<>();

    public static AiInvokeResponse fail(String msg) {
        return AiInvokeResponse.builder().success(false).errorMessage(msg).build();
    }
}
