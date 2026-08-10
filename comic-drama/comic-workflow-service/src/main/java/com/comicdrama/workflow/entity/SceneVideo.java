package com.comicdrama.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.comicdrama.common.entity.BaseTimeEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 场景视频表（步骤7产物：视频生成，按场景分组生成） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("scene_video")
public class SceneVideo extends BaseTimeEntity {

    private Long taskId;
    private Long sceneGroupId;
    private String videoUrl;
    private String thumbnailUrl;
    private String baseFrameUrl;
    private String storyboardIds;
    private String storyboardSeqRange;
    private Integer frameCount;
    private BigDecimal duration;
    private String resolution;
    private String generateParams;
    private Integer status;
    private Integer regenerateCount;
}
