package com.comicdrama.common.enums;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * AI 模型能力声明枚举。
 * 能力来源：Invoker 静态声明（capabilities()）+ ai_model_config.capabilities JSON 配置，二者取并集。
 * 步骤 Handler 可通过 modelSupports(step, capability) 声明式查询模型能力，决定是否走某条路径。
 */
@Getter
@AllArgsConstructor
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum ModelCapability {

    STREAMING("流式输出", "支持 SSE 流式返回，文本步骤可实时显示生成内容"),
    IMAGE_TO_IMAGE("图生图", "支持基于参考图生成衍生图像"),
    FIRST_FRAME_LOCK("首帧锁定", "视频生成支持首帧锁定，确保画面连续性"),
    MULTI_VOICE("多音色", "语音合成支持多种音色切换"),
    FUNCTION_CALLING("函数调用", "支持 OpenAI function calling 工具调用"),
    VISION_INPUT("视觉输入", "支持图像作为输入理解"),
    LONG_CONTEXT("长上下文", "支持超过 32K 的长上下文输入");

    /** 能力中文名 */
    private final String desc;
    /** 说明 */
    private final String description;

    /**
     * 能力标识（枚举名），用于 JSON 序列化与前端多选值。
     */
    public String getCode() {
        return name();
    }

    /**
     * 从字符串解析能力枚举（容错：未知值返回 null 而非抛异常）。
     */
    public static ModelCapability fromCode(String code) {
        if (code == null || code.isEmpty()) {
            return null;
        }
        try {
            return ModelCapability.valueOf(code.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 解析 JSON 数组字符串为能力集合，如 '["STREAMING","IMAGE_TO_IMAGE"]' → Set。
     */
    public static java.util.Set<ModelCapability> parseSet(String jsonArrayStr) {
        if (jsonArrayStr == null || jsonArrayStr.isEmpty() || "null".equals(jsonArrayStr)) {
            return java.util.Collections.emptySet();
        }
        // 去除中括号和引号，按逗号分割
        String cleaned = jsonArrayStr.replaceAll("[\\[\\]\"\\s]", "");
        if (cleaned.isEmpty()) {
            return java.util.Collections.emptySet();
        }
        return Arrays.stream(cleaned.split(","))
                .map(ModelCapability::fromCode)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
    }
}
