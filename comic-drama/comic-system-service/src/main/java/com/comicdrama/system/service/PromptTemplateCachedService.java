package com.comicdrama.system.service;

import com.comicdrama.common.constant.CacheConstants;
import com.comicdrama.common.dto.PageQuery;
import com.comicdrama.common.result.PageResult;
import com.comicdrama.system.entity.PromptTemplate;
import com.comicdrama.system.entity.PromptTemplateVersion;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * PromptTemplateService 装饰器：按 templateCode 缓存模板内容，
 * 减少对 DB 的直接查询，实现热生效。
 */
@Service
@RequiredArgsConstructor
public class PromptTemplateCachedService {

    private final PromptTemplateService promptTemplateService;
    private final Cache<String, Object> promptTemplateCache;

    public PromptTemplate getByTemplateCode(String templateCode) {
        String cacheKey = CacheConstants.CACHE_PROMPT_TEMPLATE + ":" + templateCode;
        Object cached = promptTemplateCache.getIfPresent(cacheKey);
        if (cached instanceof PromptTemplate template) {
            return template;
        }
        PromptTemplate result = promptTemplateService.lambdaQuery()
                .eq(PromptTemplate::getTemplateCode, templateCode)
                .eq(PromptTemplate::getIsEnabled, 1)
                .one();
        if (result != null) {
            promptTemplateCache.put(cacheKey, result);
        }
        return result;
    }

    public void invalidateByCode(String templateCode) {
        String cacheKey = CacheConstants.CACHE_PROMPT_TEMPLATE + ":" + templateCode;
        promptTemplateCache.invalidate(cacheKey);
    }

    public void invalidateAll() {
        promptTemplateCache.invalidateAll();
    }

    public PageResult<PromptTemplate> page(PageQuery query, String keyword, Integer stage) {
        return promptTemplateService.page(query, keyword, stage);
    }

    public void createTemplate(PromptTemplate template, String changeLog) {
        promptTemplateService.createTemplate(template, changeLog);
    }

    public void updateTemplate(PromptTemplate template, String changeLog) {
        promptTemplateService.updateTemplate(template, changeLog);
        invalidateByCode(template.getTemplateCode());
    }

    public List<PromptTemplateVersion> listVersions(Long templateId) {
        return promptTemplateService.listVersions(templateId);
    }

    public void rollback(Long templateId, Integer versionNo) {
        promptTemplateService.rollback(templateId, versionNo);
        PromptTemplate template = promptTemplateService.getById(templateId);
        if (template != null) {
            invalidateByCode(template.getTemplateCode());
        }
    }
}
