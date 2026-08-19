package com.comicdrama.workflow.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.comicdrama.common.dto.PageQuery;
import com.comicdrama.common.result.PageResult;
import com.comicdrama.workflow.entity.PromptTemplate;
import com.comicdrama.workflow.entity.PromptTemplateVersion;

import java.util.List;

/**
 * 提示词模板服务（含版本管理与回滚）
 */
public interface PromptTemplateService extends IService<PromptTemplate> {

    PageResult<PromptTemplate> page(PageQuery query, String keyword, Integer stage);

    /** 创建模板并生成版本 1 */
    void createTemplate(PromptTemplate template, String changeLog);

    /**
     * 更新模板；当 content 变化时自动生成新版本并切换为当前生效。
     */
    void updateTemplate(PromptTemplate template, String changeLog);

    /** 模板历史版本列表 */
    List<PromptTemplateVersion> listVersions(Long templateId);

    /**
     * 回滚到指定版本：更新 template.current_version + content，
     * 并将目标版本置为 is_current=1（其余置 0）。
     */
    void rollback(Long templateId, Integer versionNo);
}
