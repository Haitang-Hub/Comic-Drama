package com.comicdrama.task.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.comicdrama.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 漫剧任务表（核心表，承接用户全局参数与任务状态）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("comic_task")
public class ComicTask extends BaseEntity {

    /** 任务编号（业务唯一） */
    private String taskNo;

    private Long userId;

    private String title;

    /** 故事需求（用户输入文本） */
    private String storyRequirement;

    /** 剧情时长（秒） */
    private Integer duration;

    /** 画面比例（16:9/9:16/1:1/4:3） */
    private String aspectRatio;

    /** 分辨率（720p/1080p/2k/4k） */
    private String resolution;

    /** 配音开关：0关闭 1开启 */
    private Integer voiceEnabled;

    /** 执行模式：0全自动 1人工审核 */
    private Integer execMode;

    /** 画风（基础视觉技法） */
    private String artStyle;

    /** 风格（美学取向/文化调性） */
    private String visualStyle;

    /** 任务状态：0排队 1生成中 2已完成 3失败 4已暂停 */
    private Integer status;

    /** 当前执行步骤（1-8） */
    private Integer currentStep;

    private Integer progress;

    private Integer failureStep;

    private String failureReason;

    private String failureDetail;

    private Integer retryCount;

    private Integer queuePosition;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer totalConsumeTime;

    private String coverUrl;

    private String finalVideoUrl;

    /** 成片 manifest.json 内容（包含视频片段列表，用于在线播放） */
    private String finalWorkManifest;

    private String remark;
}
