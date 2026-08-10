package com.comicdrama.task.controller;

import com.comicdrama.common.dto.PageQuery;
import com.comicdrama.common.result.PageResult;
import com.comicdrama.common.result.Result;
import com.comicdrama.task.entity.TaskQueue;
import com.comicdrama.task.service.TaskQueueReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 任务队列（只读分页）
 */
@RestController
@RequestMapping("/api/queue")
@RequiredArgsConstructor
public class TaskQueueController {

    private final TaskQueueReadService taskQueueReadService;

    @GetMapping("/page")
    public Result<PageResult<TaskQueue>> page(PageQuery query,
                                                @RequestParam(required = false) Integer queueStatus) {
        return Result.ok(taskQueueReadService.page(query, queueStatus));
    }
}
