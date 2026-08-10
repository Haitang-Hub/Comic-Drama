package com.comicdrama.workflow.handler;

import com.comicdrama.common.enums.ModelType;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 9 步流水线步骤枚举。
 * 对应漫剧生成的完整工作流：摘要 → 分镜 → 资产设计 → 资产绘图 → 衍生绘图 → 分镜绘图 → 配音合成 → 视频生成 → 视频合并。
 * 其中步骤9（视频合并）为纯算法处理，不涉及 AI 模型调用。
 * 步骤4（资产绘图）为文生图，步骤5（衍生绘图）为图生图（基于上一版本图片生成衍生版本）。
 */
@Getter
@AllArgsConstructor
public enum StepEnum {

    SUMMARY(1, "故事摘要", "summary", ModelType.TEXT, "modelscope", true),
    STORYBOARD(2, "分镜脚本", "storyboard", ModelType.TEXT, "modelscope", true),
    ASSET_DESIGN(3, "资产设计", "asset_design", ModelType.TEXT, "modelscope", true),
    ASSET_IMAGE(4, "资产绘图", "asset_image", ModelType.IMAGE, "modelscope", true),
    ASSET_DERIVE(5, "衍生绘图", "asset_derive", ModelType.IMAGE, "modelscope", true),
    STORYBOARD_IMAGE(6, "分镜绘图", "storyboard_image", ModelType.IMAGE, "modelscope", true),
    AUDIO(7, "配音合成", "audio", ModelType.AUDIO, "seed_tts", true),
    VIDEO(8, "视频生成", "video", ModelType.VIDEO, "seedance_video", true),
    VIDEO_MERGE(9, "视频合并", "video_merge", ModelType.VIDEO, "seedance_video", false);

    /** 步骤顺序（1-9） */
    private final int order;
    /** 步骤中文名 */
    private final String name;
    /** 步骤编码（对应节点标识） */
    private final String code;
    /** 模型类型 */
    private final ModelType modelType;
    /** 模型服务商（用于路由到对应 Invoker） */
    private final String modelProvider;
    /** 是否需要 AI 模型调用（步骤9为算法处理，不需要） */
    private final boolean modelRequired;

    /**
     * 获取系统配置中的绑定键，用于从 system_config 表查找管理员配置的模型编码。
     * 对于不需要模型的步骤，返回 null。
     */
    public String getModelBindingKey() {
        if (!modelRequired) {
            return null;
        }
        return "step_" + this.code + "_model";
    }

    public static StepEnum of(Integer order) {
        if (order == null) {
            return null;
        }
        for (StepEnum s : values()) {
            if (s.order == order) {
                return s;
            }
        }
        return null;
    }
}