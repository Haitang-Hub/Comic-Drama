package com.comicdrama.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 队列状态：0等待中 1执行中 2已完成 3已取消 */
@Getter
@AllArgsConstructor
public enum QueueStatus {
    WAITING(0, "等待中"),
    RUNNING(1, "执行中"),
    DONE(2, "已完成"),
    CANCELED(3, "已取消");

    private final int code;
    private final String desc;
}
