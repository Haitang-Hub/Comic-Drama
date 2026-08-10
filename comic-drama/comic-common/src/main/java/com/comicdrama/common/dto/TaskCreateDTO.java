package com.comicdrama.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 任务创建请求 DTO（跨模块共享）。
 */
@Data
public class TaskCreateDTO {

    private String title;

    @NotBlank(message = "故事需求不能为空")
    private String storyRequirement;

    @Min(value = 5, message = "剧情时长至少5秒")
    private Integer duration = 60;

    private String aspectRatio = "16:9";

    private String resolution = "1080p";

    /** 配音开关：0关闭 1开启（默认关闭，后续可扩展） */
    private Integer voiceEnabled = 0;

    /** 执行模式：0全自动 1人工审核（每步完成后暂停等待审核） */
    private Integer execMode = 0;

    /** 画风（基础视觉技法：realistic/2d/3d/oil/watercolor/pixel） */
    private String artStyle;

    /** 风格（美学取向：chinese/shinkai/manhwa/dark_fairy/cyberpunk/anime） */
    private String visualStyle;

    private String remark;
}
