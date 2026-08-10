package com.comicdrama.task.controller;

import com.comicdrama.common.dto.PageQuery;
import com.comicdrama.common.result.PageResult;
import com.comicdrama.common.result.Result;
import com.comicdrama.task.entity.TaskFailureLog;
import com.comicdrama.task.service.TaskFailureLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 任务失败日志（只读分页 + 按任务查询 + 删除）
 */
@RestController
@RequestMapping("/api/failure")
@RequiredArgsConstructor
public class TaskFailureLogController {

    private final TaskFailureLogService taskFailureLogService;

    @GetMapping("/page")
    public Result<PageResult<TaskFailureLog>> page(PageQuery query,
                                                     @RequestParam(required = false) Long taskId) {
        return Result.ok(taskFailureLogService.page(query, taskId));
    }

    @GetMapping("/list")
    public Result<List<TaskFailureLog>> listByTaskId(@RequestParam Long taskId) {
        return Result.ok(taskFailureLogService.listByTaskId(taskId));
    }

    @DeleteMapping("/clear")
    public Result<Void> clearByTaskId(@RequestParam Long taskId) {
        taskFailureLogService.deleteByTaskId(taskId);
        return Result.ok();
    }
}
