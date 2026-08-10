package com.comicdrama.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.comicdrama.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("prompt_template")
public class PromptTemplate extends BaseEntity {
    private String templateCode;
    private String templateName;
    private Integer stage;
    private String content;
    private String variables;
    private String description;
    private Integer currentVersion;
    private Integer isEnabled;
    private Long createBy;
}