package com.comicdrama.task.schedule;

import com.comicdrama.task.service.TaskStatisticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 统计定时任务。
 * 每小时执行一次当日数据聚合，每小时整点重算当天及补算前一天。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StatisticsScheduler {

    private final TaskStatisticsService taskStatisticsService;

    /**
     * 每小时执行一次聚合（每小时第 0 分钟）。
     * 聚合当天数据，并补算前一天数据。
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void hourlyAggregate() {
        log.info("=== Starting hourly statistics aggregation ===");

        try {
            LocalDate today = LocalDate.now();
            taskStatisticsService.dailyAggregate(today);
            log.info("Aggregated statistics for today: {}", today);
        } catch (Exception e) {
            log.error("Failed to aggregate today's statistics", e);
        }

        try {
            LocalDate yesterday = LocalDate.now().minusDays(1);
            taskStatisticsService.dailyAggregate(yesterday);
            log.info("Aggregated statistics for yesterday: {}", yesterday);
        } catch (Exception e) {
            log.error("Failed to aggregate yesterday's statistics", e);
        }

        log.info("=== Hourly statistics aggregation completed ===");
    }
}
