package com.comicdrama.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 资源文件类型：1图片 2音频 3视频 4文档 5其他 */
@Getter
@AllArgsConstructor
public enum FileType {
    IMAGE(1, "图片"),
    AUDIO(2, "音频"),
    VIDEO(3, "视频"),
    DOC(4, "文档"),
    OTHER(5, "其他");

    private final int code;
    private final String desc;
}
