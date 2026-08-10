package com.comicdrama.system.controller;

import com.comicdrama.common.dto.PageQuery;
import com.comicdrama.common.result.PageResult;
import com.comicdrama.common.result.Result;
import com.comicdrama.system.dto.PromptTemplateSaveDTO;
import com.comicdrama.system.entity.PromptTemplate;
import com.comicdrama.system.entity.PromptTemplateVersion;
import com.comicdrama.system.service.PromptTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 提示词模板管理（管理员，含版本回滚）
 */
@RestController
@RequestMapping("/api/template")
@RequiredArgsConstructor
public class PromptTemplateController {

    private final PromptTemplateService promptTemplateService;

    @GetMapping("/page")
    public Result<PageResult<PromptTemplate>> page(PageQuery query,
                                                    @RequestParam(required = false) String keyword,
                                                    @RequestParam(required = false) Integer stage) {
        return Result.ok(promptTemplateService.page(query, keyword, stage));
    }

    @GetMapping("/{id}")
    public Result<PromptTemplate> get(@PathVariable Long id) {
        return Result.ok(promptTemplateService.getById(id));
    }

    @PostMapping
    public Result<Void> add(@RequestBody PromptTemplateSaveDTO dto) {
        PromptTemplate template = new PromptTemplate();
        BeanUtils.copyProperties(dto, template);
        promptTemplateService.createTemplate(template, dto.getChangeLog());
        return Result.ok();
    }

    @PutMapping
    public Result<Void> update(@RequestBody PromptTemplateSaveDTO dto) {
        PromptTemplate template = new PromptTemplate();
        BeanUtils.copyProperties(dto, template);
        promptTemplateService.updateTemplate(template, dto.getChangeLog());
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        promptTemplateService.removeById(id);
        return Result.ok();
    }

    /** 模板历史版本列表 */
    @GetMapping("/{id}/version")
    public Result<List<PromptTemplateVersion>> listVersions(@PathVariable Long id) {
        return Result.ok(promptTemplateService.listVersions(id));
    }

    /** 回滚到指定版本 */
    @PutMapping("/{id}/rollback/{versionNo}")
    public Result<Void> rollback(@PathVariable Long id, @PathVariable Integer versionNo) {
        promptTemplateService.rollback(id, versionNo);
        return Result.ok();
    }
}
