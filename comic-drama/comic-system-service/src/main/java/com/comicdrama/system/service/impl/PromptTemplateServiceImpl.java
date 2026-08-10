package com.comicdrama.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.comicdrama.common.dto.PageQuery;
import com.comicdrama.common.exception.BizException;
import com.comicdrama.common.result.PageResult;
import com.comicdrama.common.result.ResultCode;
import com.comicdrama.common.util.SecurityUtils;
import com.comicdrama.system.entity.PromptTemplate;
import com.comicdrama.system.entity.PromptTemplateVersion;
import com.comicdrama.system.mapper.PromptTemplateMapper;
import com.comicdrama.system.mapper.PromptTemplateVersionMapper;
import com.comicdrama.system.service.PromptTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PromptTemplateServiceImpl extends ServiceImpl<PromptTemplateMapper, PromptTemplate> implements PromptTemplateService {

    private final PromptTemplateVersionMapper versionMapper;

    @Override
    public PageResult<PromptTemplate> page(PageQuery query, String keyword, Integer stage) {
        LambdaQueryWrapper<PromptTemplate> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(PromptTemplate::getTemplateCode, keyword)
                    .or().like(PromptTemplate::getTemplateName, keyword);
        }
        if (stage != null) {
            wrapper.eq(PromptTemplate::getStage, stage);
        }
        wrapper.orderByAsc(PromptTemplate::getStage)
                .orderByDesc(PromptTemplate::getCreateTime);
        Page<PromptTemplate> page = new Page<>(query.getPage(), query.getSize());
        Page<PromptTemplate> result = this.page(page, wrapper);
        return new PageResult<>(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createTemplate(PromptTemplate template, String changeLog) {
        if (template.getCurrentVersion() == null) {
            template.setCurrentVersion(1);
        }
        if (template.getIsEnabled() == null) {
            template.setIsEnabled(1);
        }
        template.setCreateBy(SecurityUtils.getCurrentUserIdOrNull());
        this.save(template);
        // 生成初始版本 1
        saveVersion(template, 1, template.getContent(), template.getVariables(), changeLog);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTemplate(PromptTemplate template, String changeLog) {
        PromptTemplate exists = this.getById(template.getId());
        if (exists == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND);
        }
        boolean contentChanged = StringUtils.hasText(template.getContent())
                && !template.getContent().equals(exists.getContent());
        if (contentChanged) {
            // 生成新版本并切换为当前生效
            int newVersionNo = (exists.getCurrentVersion() == null ? 0 : exists.getCurrentVersion()) + 1;
            // 旧版本全部置为非当前
            PromptTemplateVersion reset = new PromptTemplateVersion();
            reset.setIsCurrent(0);
            versionMapper.update(reset, new LambdaQueryWrapper<PromptTemplateVersion>()
                    .eq(PromptTemplateVersion::getTemplateId, template.getId()));
            saveVersion(template, newVersionNo, template.getContent(), template.getVariables(), changeLog);
            template.setCurrentVersion(newVersionNo);
        }
        this.updateById(template);
    }

    @Override
    public List<PromptTemplateVersion> listVersions(Long templateId) {
        return versionMapper.selectList(new LambdaQueryWrapper<PromptTemplateVersion>()
                .eq(PromptTemplateVersion::getTemplateId, templateId)
                .orderByDesc(PromptTemplateVersion::getVersionNo));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rollback(Long templateId, Integer versionNo) {
        PromptTemplate template = this.getById(templateId);
        if (template == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND);
        }
        PromptTemplateVersion target = versionMapper.selectOne(new LambdaQueryWrapper<PromptTemplateVersion>()
                .eq(PromptTemplateVersion::getTemplateId, templateId)
                .eq(PromptTemplateVersion::getVersionNo, versionNo));
        if (target == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "版本不存在");
        }
        // 更新模板内容与当前版本号
        PromptTemplate update = new PromptTemplate();
        update.setId(templateId);
        update.setContent(target.getContent());
        update.setVariables(target.getVariables());
        update.setCurrentVersion(versionNo);
        this.updateById(update);
        // 切换 is_current
        PromptTemplateVersion reset = new PromptTemplateVersion();
        reset.setIsCurrent(0);
        versionMapper.update(reset, new LambdaQueryWrapper<PromptTemplateVersion>()
                .eq(PromptTemplateVersion::getTemplateId, templateId));
        PromptTemplateVersion markCurrent = new PromptTemplateVersion();
        markCurrent.setId(target.getId());
        markCurrent.setIsCurrent(1);
        versionMapper.updateById(markCurrent);
    }

    private void saveVersion(PromptTemplate template, int versionNo, String content, String variables, String changeLog) {
        PromptTemplateVersion version = new PromptTemplateVersion();
        version.setTemplateId(template.getId());
        version.setVersionNo(versionNo);
        version.setContent(content);
        version.setVariables(variables);
        version.setChangeLog(changeLog);
        version.setIsCurrent(1);
        version.setCreatedBy(SecurityUtils.getCurrentUserIdOrNull());
        versionMapper.insert(version);
    }
}
