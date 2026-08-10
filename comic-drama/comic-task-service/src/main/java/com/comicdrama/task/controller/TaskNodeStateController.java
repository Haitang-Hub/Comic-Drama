package com.comicdrama.task.controller;

import com.comicdrama.common.result.Result;
import com.comicdrama.task.entity.TaskNodeState;
import com.comicdrama.task.service.TaskNodeStateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 任务节点状态（只读，Phase-3 单点重生成联动）
 */
@RestController
@RequestMapping("/api/node-state")
@RequiredArgsConstructor
public class TaskNodeStateController {

    private final TaskNodeStateService taskNodeStateService;

    @GetMapping("/{taskId}")
    public Result<List<TaskNodeState>> list(@PathVariable Long taskId) {
        return Result.ok(taskNodeStateService.listByTaskId(taskId));
    }
}
