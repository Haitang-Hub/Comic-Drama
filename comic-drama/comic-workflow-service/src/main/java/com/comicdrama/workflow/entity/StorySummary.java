package com.comicdrama.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.comicdrama.common.entity.BaseTimeEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 故事摘要表（步骤1产物：摘要生成） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("story_summary")
public class StorySummary extends BaseTimeEntity {

    private Long taskId;
    private String content;
    private Integer duration;
}
