package com.comicdrama.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 任务状态：0排队 1生成中 2已完成 3失败 4已暂停 */
@Getter
@AllArgsConstructor
public enum TaskStatus {
    QUEUE(0, "排队"),
    RUNNING(1, "生成中"),
    DONE(2, "已完成"),
    FAILED(3, "失败"),
    PAUSED(4, "已暂停");

    private final int code;
    private final String desc;

    public static TaskStatus of(Integer code) {
        if (code == null) return null;
        for (TaskStatus s : values()) {
            if (s.code == code) return s;
        }
        return null;
    }
}
