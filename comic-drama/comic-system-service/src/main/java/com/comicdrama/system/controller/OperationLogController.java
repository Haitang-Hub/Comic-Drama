package com.comicdrama.system.controller;

import com.comicdrama.common.dto.PageQuery;
import com.comicdrama.common.result.PageResult;
import com.comicdrama.common.result.Result;
import com.comicdrama.system.entity.OperationLog;
import com.comicdrama.system.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 操作日志（只读分页）
 */
@RestController
@RequestMapping("/api/operation-log")
@RequiredArgsConstructor
public class OperationLogController {

    private final OperationLogService operationLogService;

    @GetMapping("/page")
    public Result<PageResult<OperationLog>> page(PageQuery query,
                                                   @RequestParam(required = false) String module,
                                                   @RequestParam(required = false) String username) {
        return Result.ok(operationLogService.page(query, module, username));
    }
}
