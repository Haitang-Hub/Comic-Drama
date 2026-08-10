package com.comicdrama.task.controller;

import com.comicdrama.common.result.Result;
import com.comicdrama.task.entity.TaskProgressLog;
import com.comicdrama.task.service.TaskProgressLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 任务进度日志（只读查询 + 写入接口供工作流服务推送）
 */
@RestController
@RequestMapping("/api/progress")
@RequiredArgsConstructor
public class TaskProgressLogController {

    private final TaskProgressLogService taskProgressLogService;

    @GetMapping("/{taskId}")
    public Result<List<TaskProgressLog>> list(@PathVariable Long taskId) {
        return Result.ok(taskProgressLogService.listByTaskId(taskId));
    }

    /** 保存一条进度日志（工作流服务内部调用） */
    @PostMapping
    public Result<Void> save(@RequestBody TaskProgressLog log) {
        taskProgressLogService.save(log);
        return Result.ok();
    }

    /** 清空指定任务的进度日志 */
    @DeleteMapping("/{taskId}")
    public Result<Void> clear(@PathVariable Long taskId) {
        taskProgressLogService.removeByTaskId(taskId);
        return Result.ok();
    }
}
