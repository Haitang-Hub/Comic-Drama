package com.comicdrama.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 节点类型：用于 task_node_state 与 progress/failure 日志 */
@Getter
@AllArgsConstructor
public enum NodeType {
    OUTLINE("outline", "大纲"),
    SCENE_GROUP("scene_group", "场景分组"),
    STORYBOARD("storyboard", "分镜"),
    IMAGE("image", "分镜画面"),
    AUDIO("audio", "分镜配音"),
    VIDEO("video", "场景视频");

    private final String code;
    private final String desc;
}
