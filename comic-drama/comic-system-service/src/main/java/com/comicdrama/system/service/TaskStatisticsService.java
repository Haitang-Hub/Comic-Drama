package com.comicdrama.system.service;

import com.comicdrama.system.entity.TaskStatisticsDaily;

import java.util.List;
import java.util.Map;

/**
 * 任务统计服务（数据看板专用）。
 * Phase-4：提供仪表盘聚合统计、作品统计、定时聚合任务。
 */
public interface TaskStatisticsService {

    /**
     * 聚合指定日期的任务数据到 task_statistics_daily。
     *
     * @param date 统计日期（当天为 null）
     */
    void dailyAggregate(java.time.LocalDate date);

    /**
     * 获取仪表盘总览统计。
     *
     * @return 总览数据（总任务数、完成率、失败率、平均耗时等）
     */
    Map<String, Object> getDashboardStats();

    /**
     * 获取作品统计数据。
     *
     * @return 作品统计（总作品数、总时长、分辨率分布等）
     */
    Map<String, Object> getWorkStats();

    /**
     * 获取最近 N 天的日趋势数据。
     *
     * @param days 天数（7/30 等）
     * @return 日趋势列表
     */
    List<TaskStatisticsDaily> getDailyTrend(int days);

    /**
     * 获取 7 步各步骤平均耗时统计。
     *
     * @return 步骤耗时映射
     */
    Map<String, Object> getStepTimeStats();
}
