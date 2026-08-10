package com.comicdrama.resource.controller;

import com.comicdrama.common.dto.PageQuery;
import com.comicdrama.common.result.PageResult;
import com.comicdrama.common.result.Result;
import com.comicdrama.resource.entity.ResourceCleanupLog;
import com.comicdrama.resource.service.ResourceCleanupLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 资源清理日志（只读分页）
 */
@RestController
@RequestMapping("/api/resource/cleanup-log")
@RequiredArgsConstructor
public class ResourceCleanupLogController {

    private final ResourceCleanupLogService resourceCleanupLogService;

    @GetMapping("/page")
    public Result<PageResult<ResourceCleanupLog>> page(PageQuery query,
                                                        @RequestParam(required = false) Long taskId) {
        return Result.ok(resourceCleanupLogService.page(query, taskId));
    }
}
