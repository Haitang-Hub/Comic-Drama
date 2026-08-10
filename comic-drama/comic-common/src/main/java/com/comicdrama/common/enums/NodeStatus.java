package com.comicdrama.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 节点状态：0待生成 1已生成 2已编辑 3已重生成 4生成失败 */
@Getter
@AllArgsConstructor
public enum NodeStatus {
    PENDING(0, "待生成"),
    DONE(1, "已生成"),
    EDITED(2, "已编辑"),
    REGENERATED(3, "已重生成"),
    FAILED(4, "生成失败");

    private final int code;
    private final String desc;
}
