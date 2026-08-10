package com.comicdrama.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.comicdrama.common.entity.BaseTimeEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 分镜配音表（步骤6产物：Seed-TTS 角色配音） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("storyboard_audio")
public class StoryboardAudio extends BaseTimeEntity {

    private Long taskId;
    private Long storyboardId;
    private String audioUrl;
    private String text;
    /** 绑定音色资产ID（asset_design.id） */
    private Long voiceAssetId;
    private String emotion;
    private Integer speed;
    private Integer emotionIntensity;
    private BigDecimal duration;
    private Integer status;
    private Integer regenerateCount;
}
