package com.comicdrama.resource.controller;

import com.comicdrama.common.dto.PageQuery;
import com.comicdrama.common.exception.BizException;
import com.comicdrama.common.result.PageResult;
import com.comicdrama.common.result.Result;
import com.comicdrama.resource.entity.ResourceFile;
import com.comicdrama.resource.service.ResourceFileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 资源文件管理（上传/分页/签名URL）
 */
@Slf4j
@RestController
@RequestMapping("/api/resource")
@RequiredArgsConstructor
public class ResourceFileController {

    private final ResourceFileService resourceFileService;

    @PostMapping("/upload")
    public Result<ResourceFile> upload(@RequestParam("file") MultipartFile file,
                                       @RequestParam(value = "taskId", required = false) Long taskId,
                                       @RequestParam(value = "sourceType", required = false) String sourceType) {
        try {
            return Result.ok(resourceFileService.upload(file, taskId, sourceType));
        } catch (Exception e) {
            log.error("文件上传失败", e);
            throw new BizException("文件上传失败：" + e.getMessage());
        }
    }

    @GetMapping("/page")
    public Result<PageResult<ResourceFile>> page(PageQuery query,
                                                  @RequestParam(required = false) Long taskId,
                                                  @RequestParam(required = false) Integer fileType) {
        return Result.ok(resourceFileService.page(query, taskId, fileType));
    }

    @GetMapping("/{id}")
    public Result<ResourceFile> get(@PathVariable Long id) {
        return Result.ok(resourceFileService.getById(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        try {
            resourceFileService.delete(id);
        } catch (Exception e) {
            log.error("删除资源文件失败", e);
            throw new BizException("删除资源文件失败：" + e.getMessage());
        }
        return Result.ok();
    }

    /** 生成临时签名URL */
    @GetMapping("/{id}/sign-url")
    public Result<String> signUrl(@PathVariable Long id,
                                   @RequestParam(defaultValue = "3600") int expire) {
        try {
            return Result.ok(resourceFileService.signUrl(id, expire));
        } catch (Exception e) {
            log.error("生成签名URL失败", e);
            throw new BizException("生成签名URL失败：" + e.getMessage());
        }
    }
}
