package com.comicdrama.system.controller;

import com.comicdrama.common.dto.PageQuery;
import com.comicdrama.common.result.PageResult;
import com.comicdrama.common.result.Result;
import com.comicdrama.system.entity.TaskStatisticsDaily;
import com.comicdrama.system.service.TaskStatisticsDailyService;
import com.comicdrama.system.service.TaskStatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 任务统计控制器（数据看板 API）。
 */
@RestController
@RequestMapping("/api/statistics")
@RequiredArgsConstructor
public class TaskStatisticsDailyController {

    private final TaskStatisticsDailyService taskStatisticsDailyService;
    private final TaskStatisticsService taskStatisticsService;

    @GetMapping("/daily/page")
    public Result<PageResult<TaskStatisticsDaily>> page(PageQuery query) {
        return Result.ok(taskStatisticsDailyService.page(query));
    }

    @GetMapping("/dashboard")
    public Result<Map<String, Object>> getDashboardStats() {
        return Result.ok(taskStatisticsService.getDashboardStats());
    }

    @GetMapping("/work")
    public Result<Map<String, Object>> getWorkStats() {
        return Result.ok(taskStatisticsService.getWorkStats());
    }

    @GetMapping("/trend")
    public Result<List<TaskStatisticsDaily>> getDailyTrend(
            @RequestParam(defaultValue = "7") int days) {
        return Result.ok(taskStatisticsService.getDailyTrend(days));
    }

    @GetMapping("/step-time")
    public Result<Map<String, Object>> getStepTimeStats() {
        return Result.ok(taskStatisticsService.getStepTimeStats());
    }

    @PostMapping("/aggregate")
    public Result<Void> triggerAggregate(@RequestParam(required = false) String date) {
        java.time.LocalDate aggDate = date != null
                ? java.time.LocalDate.parse(date)
                : java.time.LocalDate.now();
        taskStatisticsService.dailyAggregate(aggDate);
        return Result.ok();
    }
}
