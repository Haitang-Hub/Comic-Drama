package com.comicdrama.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 8 步工作流步骤 */
@Getter
@AllArgsConstructor
public enum WorkflowStep {
    SUMMARY(1, "故事摘要"),
    STORYBOARD(2, "分镜脚本"),
    ASSET_DESIGN(3, "资产设计"),
    ASSET_IMAGE(4, "资产绘图"),
    STORYBOARD_IMAGE(5, "分镜绘图"),
    AUDIO(6, "配音合成"),
    VIDEO(7, "视频生成"),
    VIDEO_MERGE(8, "视频合并");

    private final int code;
    private final String desc;

    public static WorkflowStep of(Integer code) {
        if (code == null) return null;
        for (WorkflowStep s : values()) {
            if (s.code == code) return s;
        }
        return null;
    }
}
