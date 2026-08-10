package com.comicdrama.common.enums;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AI 模型调用协议枚举。
 * 协议标识描述"如何调用 API"，与服务商（provider）解耦：
 * 同一 provider 可能支持多种协议；同一协议也可能被多个 provider 共用（如 openai-chat）。
 *
 * 协议化改造后，新增 OpenAI 兼容服务商只需在 ai_model_config 表配置 protocol=openai-chat，无需写代码。
 * custom-http 前缀的协议由 CustomHttpInvoker 按 YAML 模板驱动，支持任意 HTTP API。
 */
@Getter
@AllArgsConstructor
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum ModelProtocol {

    OPENAI_CHAT("openai-chat", "OpenAI 兼容对话", new Integer[]{1}, "OpenAI /chat/completions 兼容格式，覆盖 deepseek/modelscope/Qwen 等"),
    MODELSCOPE_IMAGE("modelscope-image", "ModelScope 图像生成", new Integer[]{2}, "ModelScope 平台图像生成，异步提交+轮询"),
    ARK_IMAGE("ark-image", "字节 Ark 图像生成", new Integer[]{2}, "字节 Ark /images/generations 同步图像生成"),
    ARK_TTS("ark-tts", "字节 Ark 语音合成", new Integer[]{3}, "字节 Ark /tts 语音合成"),
    ARK_VIDEO("ark-video", "字节 Ark 视频生成", new Integer[]{4}, "字节 Ark 视频生成，异步提交+轮询"),
    CUSTOM_HTTP("custom-http", "自定义 HTTP 模板", new Integer[]{1, 2, 3, 4}, "配置驱动万能 HTTP 适配器，protocol 值格式 custom-http-{模板名}");

    /** 协议标识（存入 ai_model_config.protocol 字段） */
    private final String code;
    /** 协议中文名 */
    private final String desc;
    /** 支持的模型类型：1文本 2图像 3音频 4视频 */
    private final Integer[] supportedTypes;
    /** 说明 */
    private final String description;

    /**
     * 根据协议标识查找枚举。
     * custom-http-xxx 前缀统一匹配 CUSTOM_HTTP。
     */
    public static ModelProtocol fromCode(String code) {
        if (code == null || code.isEmpty()) {
            return null;
        }
        // custom-http 前缀统一匹配
        if (code.startsWith("custom-http")) {
            return CUSTOM_HTTP;
        }
        for (ModelProtocol protocol : values()) {
            if (protocol.code.equals(code)) {
                return protocol;
            }
        }
        return null;
    }

    /**
     * 判断协议标识是否合法（含 custom-http-xxx 变体）。
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }

    /**
     * 返回所有可选协议（用于前端下拉）。
     */
    public static List<ModelProtocol> allProtocols() {
        return Arrays.asList(values());
    }
}
