package com.comicdrama.workflow.dto;

import lombok.Builder;
import lombok.Data;

/**
 * AI 模型连通性测试结果。
 */
@Data
@Builder
public class AiModelTestResultDTO {

    /** 是否连通成功 */
    private Boolean success;

    /** HTTP 状态码（请求未发出或网络异常时为 null） */
    private Integer statusCode;

    /** 耗时（毫秒） */
    private Long latencyMs;

    /** 结果说明：成功时为简要响应摘要，失败时为错误原因 */
    private String message;

    public static AiModelTestResultDTO ok(int statusCode, long latencyMs, String message) {
        return AiModelTestResultDTO.builder()
                .success(true)
                .statusCode(statusCode)
                .latencyMs(latencyMs)
                .message(message)
                .build();
    }

    public static AiModelTestResultDTO fail(int statusCode, long latencyMs, String message) {
        return AiModelTestResultDTO.builder()
                .success(false)
                .statusCode(statusCode)
                .latencyMs(latencyMs)
                .message(message)
                .build();
    }

    public static AiModelTestResultDTO fail(long latencyMs, String message) {
        return AiModelTestResultDTO.builder()
                .success(false)
                .latencyMs(latencyMs)
                .message(message)
                .build();
    }
}
