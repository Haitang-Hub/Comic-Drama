package com.comicdrama.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.comicdrama.common.entity.BaseTimeEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("prompt_template_version")
public class PromptTemplateVersion extends BaseTimeEntity {
    private Long templateId;
    private Integer versionNo;
    private String content;
    private String variables;
    private String changeLog;
    private Integer isCurrent;
    private Long createdBy;
}