package com.comicdrama.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.comicdrama.common.entity.BaseTimeEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 分镜画面表（步骤5产物：Seedream 递进式图生图） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("storyboard_image")
public class StoryboardImage extends BaseTimeEntity {

    private Long taskId;
    private Long storyboardId;
    private String imageUrl;
    private String thumbnailUrl;
    private Long baseImageId;
    private String promptUsed;
    private String characterRefs;
    private String sceneRefs;
    private String propRefs;
    private Integer status;
    private Integer regenerateCount;
    private Integer width;
    private Integer height;
}
