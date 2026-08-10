package com.comicdrama.workflow.controller;

import com.comicdrama.common.dto.PageQuery;
import com.comicdrama.common.result.PageResult;
import com.comicdrama.common.result.Result;
import com.comicdrama.workflow.entity.TokenUsageLog;
import com.comicdrama.workflow.service.TokenUsageLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin/token-usage")
@RequiredArgsConstructor
public class TokenUsageLogController {

    private final TokenUsageLogService tokenUsageLogService;

    @GetMapping("/page")
    public Result<PageResult<TokenUsageLog>> page(PageQuery query,
                                                   @RequestParam(required = false) Long taskId,
                                                   @RequestParam(required = false) String modelProvider,
                                                   @RequestParam(required = false) String modelName,
                                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.ok(tokenUsageLogService.pageAdmin(query, taskId, modelProvider, modelName, startDate, endDate));
    }

    @GetMapping("/{id}")
    public Result<TokenUsageLog> get(@PathVariable Long id) {
        return Result.ok(tokenUsageLogService.getById(id));
    }

    @GetMapping("/aggregate")
    public Result<Map<String, Object>> aggregate(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.ok(tokenUsageLogService.aggregate(startDate, endDate));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        tokenUsageLogService.removeById(id);
        return Result.ok();
    }
}
