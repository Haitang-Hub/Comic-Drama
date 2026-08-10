package com.comicdrama.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 素材类型：1人物 2场景 3道具 4音色 */
@Getter
@AllArgsConstructor
public enum MaterialType {
    CHARACTER(1, "人物"),
    SCENE(2, "场景"),
    PROP(3, "道具"),
    VOICE(4, "音色");

    private final int code;
    private final String desc;
}
