package com.comicdrama.system.dto;

import com.comicdrama.system.entity.PromptTemplate;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 提示词模板保存请求（携带变更说明，用于版本记录）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PromptTemplateSaveDTO extends PromptTemplate {

    /** 变更说明（写入版本 change_log） */
    private String changeLog;
}
