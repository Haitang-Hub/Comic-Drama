package com.comicdrama.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.comicdrama.common.entity.BaseTimeEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 素材提示词表（步骤4产物：人物/场景/道具/音色 长期变化） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("material_prompt")
public class MaterialPrompt extends BaseTimeEntity {

    private Long taskId;
    private Integer materialType;
    private String materialCode;
    private String materialName;
    private String promptContent;
    private String referenceImageUrl;
    private String voiceSampleUrl;
    private Integer startStoryboardSeq;
    private Integer endStoryboardSeq;
    private Integer isLongTerm;
    private Long predecessorId;
}
