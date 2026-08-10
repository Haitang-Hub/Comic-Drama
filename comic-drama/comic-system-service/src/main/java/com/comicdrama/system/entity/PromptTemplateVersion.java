package com.comicdrama.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.comicdrama.common.entity.BaseCreateTimeEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 提示词模板版本表（版本备份、回滚支持）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("prompt_template_version")
public class PromptTemplateVersion extends BaseCreateTimeEntity {

    private Long templateId;

    private Integer versionNo;

    /** 该版本模板内容 */
    private String content;

    /** 该版本变量列表（JSON） */
    private String variables;

    private String changeLog;

    /** 是否当前生效版本：0否 1是 */
    private Integer isCurrent;

    private Long createdBy;
}
