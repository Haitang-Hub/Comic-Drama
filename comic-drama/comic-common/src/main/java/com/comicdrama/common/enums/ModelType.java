package com.comicdrama.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** AI 模型类型：1文本生成 2图片生成 3音频生成 4视频生成 */
@Getter
@AllArgsConstructor
public enum ModelType {
    TEXT(1, "文本生成"),
    IMAGE(2, "图片生成"),
    AUDIO(3, "音频生成"),
    VIDEO(4, "视频生成");

    private final int code;
    private final String desc;

    public static ModelType fromCode(Integer code) {
        if (code == null) return null;
        for (ModelType type : values()) {
            if (type.code == code) return type;
        }
        return null;
    }
}
