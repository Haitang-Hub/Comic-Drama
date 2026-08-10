package com.comicdrama.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.comicdrama.common.enums.TaskStatus;
import com.comicdrama.system.entity.TaskStatisticsDaily;
import com.comicdrama.system.mapper.TaskStatisticsDailyMapper;
import com.comicdrama.system.service.TaskStatisticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskStatisticsServiceImpl implements TaskStatisticsService {

    private final TaskStatisticsDailyMapper statisticsMapper;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void dailyAggregate(LocalDate date) {
        if (date == null) {
            date = LocalDate.now();
        }

        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(LocalTime.MAX);

        log.info("Starting daily aggregation for date: {}", date);

        TaskStatisticsDaily stats = new TaskStatisticsDaily();
        stats.setStatDate(date);

        Integer totalTaskCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM comic_task WHERE create_time >= ? AND create_time <= ? AND deleted = 0",
                Integer.class, startOfDay, endOfDay);
        stats.setTotalTaskCount(totalTaskCount != null ? totalTaskCount : 0);

        Integer successCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT t.id) FROM comic_task t " +
                        "WHERE t.deleted = 0 AND t.create_time >= ? AND t.create_time <= ? " +
                        "AND (t.status = ? OR EXISTS (SELECT 1 FROM task_node_state ns " +
                        "WHERE ns.task_id = t.id AND ns.node_status = 2))",
                Integer.class, startOfDay, endOfDay, TaskStatus.DONE.getCode());
        stats.setSuccessCount(successCount != null ? successCount : 0);

        Integer failureCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM comic_task WHERE status = ? AND create_time >= ? AND create_time <= ? AND deleted = 0",
                Integer.class, TaskStatus.FAILED.getCode(), startOfDay, endOfDay);
        stats.setFailureCount(failureCount != null ? failureCount : 0);

        int total = stats.getTotalTaskCount() != null ? stats.getTotalTaskCount() : 0;
        if (total > 0) {
            stats.setSuccessRate(BigDecimal.valueOf(stats.getSuccessCount() != null ? stats.getSuccessCount() : 0)
                    .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP));
            stats.setFailureRate(BigDecimal.valueOf(stats.getFailureCount() != null ? stats.getFailureCount() : 0)
                    .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP));
        } else {
            stats.setSuccessRate(BigDecimal.ZERO);
            stats.setFailureRate(BigDecimal.ZERO);
        }

        stats.setAvgSummaryTime(calcAvgStepTime(startOfDay, endOfDay, 1));
        stats.setAvgStoryboardTime(calcAvgStepTime(startOfDay, endOfDay, 2));
        stats.setAvgAssetTime(calcAvgStepTime(startOfDay, endOfDay, 3));
        stats.setAvgImageTime(calcAvgStepTime(startOfDay, endOfDay, 5));
        stats.setAvgAudioTime(calcAvgStepTime(startOfDay, endOfDay, 6));
        stats.setAvgVideoTime(calcAvgStepTime(startOfDay, endOfDay, 7));

        int avgSummary = stats.getAvgSummaryTime() != null ? stats.getAvgSummaryTime() : 0;
        int avgStoryboard = stats.getAvgStoryboardTime() != null ? stats.getAvgStoryboardTime() : 0;
        int avgAsset = stats.getAvgAssetTime() != null ? stats.getAvgAssetTime() : 0;
        int avgImage = stats.getAvgImageTime() != null ? stats.getAvgImageTime() : 0;
        int avgAudio = stats.getAvgAudioTime() != null ? stats.getAvgAudioTime() : 0;
        int avgVideo = stats.getAvgVideoTime() != null ? stats.getAvgVideoTime() : 0;
        stats.setAvgTotalTime(avgSummary + avgStoryboard + avgAsset + avgImage + avgAudio + avgVideo);

        Integer newUserCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE create_time >= ? AND create_time <= ? AND deleted = 0",
                Integer.class, startOfDay, endOfDay);
        stats.setNewUserCount(newUserCount != null ? newUserCount : 0);

        Integer activeUserCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT user_id) FROM comic_task WHERE create_time >= ? AND create_time <= ? AND deleted = 0",
                Integer.class, startOfDay, endOfDay);
        stats.setActiveUserCount(activeUserCount != null ? activeUserCount : 0);

        Long diskUsageBytes = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(file_size), 0) FROM resource_file WHERE create_time <= ? AND deleted = 0",
                Long.class, endOfDay);
        stats.setDiskUsageBytes(diskUsageBytes != null ? diskUsageBytes : 0L);

        LambdaQueryWrapper<TaskStatisticsDaily> existWrapper = new LambdaQueryWrapper<TaskStatisticsDaily>()
                .eq(TaskStatisticsDaily::getStatDate, date);
        TaskStatisticsDaily existing = statisticsMapper.selectOne(existWrapper);

        if (existing != null) {
            stats.setId(existing.getId());
            stats.setUpdateTime(LocalDateTime.now());
            statisticsMapper.updateById(stats);
            log.info("Updated daily statistics for date: {}", date);
        } else {
            stats.setCreateTime(LocalDateTime.now());
            statisticsMapper.insert(stats);
            log.info("Inserted daily statistics for date: {}", date);
        }
    }

    private Integer calcAvgStepTime(LocalDateTime startOfDay, LocalDateTime endOfDay, int step) {
        String sql = "SELECT COALESCE(AVG(TIMESTAMPDIFF(SECOND, create_time, update_time)), 0) " +
                "FROM task_node_state WHERE step = ? AND node_status = 2 " +
                "AND create_time >= ? AND update_time <= ?";
        Long avg = jdbcTemplate.queryForObject(sql, Long.class, step, startOfDay, endOfDay);
        return avg != null ? avg.intValue() : 0;
    }

    @Override
    public Map<String, Object> getDashboardStats() {
        Map<String, Object> result = new HashMap<>();

        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        TaskStatisticsDaily todayStats = getOrInitStats(today);
        TaskStatisticsDaily yesterdayStats = getOrInitStats(yesterday);

        int totalTasks = todayStats.getTotalTaskCount() != null ? todayStats.getTotalTaskCount() : 0;
        int successCount = todayStats.getSuccessCount() != null ? todayStats.getSuccessCount() : 0;
        int failureCount = todayStats.getFailureCount() != null ? todayStats.getFailureCount() : 0;

        result.put("totalTasks", totalTasks);
        result.put("successCount", successCount);
        result.put("failureCount", failureCount);
        result.put("successRate", todayStats.getSuccessRate() != null ? todayStats.getSuccessRate() : BigDecimal.ZERO);
        result.put("failureRate", todayStats.getFailureRate() != null ? todayStats.getFailureRate() : BigDecimal.ZERO);
        result.put("avgTotalTime", todayStats.getAvgTotalTime() != null ? todayStats.getAvgTotalTime() : 0);

        int totalChange = totalTasks - (yesterdayStats.getTotalTaskCount() != null ? yesterdayStats.getTotalTaskCount() : 0);
        result.put("totalChange", totalChange);

        int successChange = successCount - (yesterdayStats.getSuccessCount() != null ? yesterdayStats.getSuccessCount() : 0);
        result.put("successChange", successChange);

        BigDecimal todayRate = todayStats.getSuccessRate() != null ? todayStats.getSuccessRate() : BigDecimal.ZERO;
        BigDecimal yesterdayRate = yesterdayStats.getSuccessRate() != null ? yesterdayStats.getSuccessRate() : BigDecimal.ZERO;
        result.put("rateChange", todayRate.subtract(yesterdayRate));

        return result;
    }

    @Override
    public Map<String, Object> getWorkStats() {
        Map<String, Object> result = new HashMap<>();

        Long totalWorks = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM comic_work WHERE deleted = 0", Long.class);
        result.put("totalWorks", totalWorks != null ? totalWorks : 0L);

        Long totalDuration = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(duration), 0) FROM comic_work WHERE deleted = 0", Long.class);
        result.put("totalDuration", totalDuration != null ? totalDuration : 0L);

        List<Map<String, Object>> distList = jdbcTemplate.queryForList(
                "SELECT resolution, COUNT(*) AS cnt FROM comic_work WHERE deleted = 0 GROUP BY resolution");
        Map<String, Integer> resolutionDistribution = new HashMap<>();
        resolutionDistribution.put("480p", 0);
        resolutionDistribution.put("720p", 0);
        resolutionDistribution.put("1080p", 0);
        resolutionDistribution.put("2K", 0);
        resolutionDistribution.put("4K", 0);
        for (Map<String, Object> row : distList) {
            String res = (String) row.get("resolution");
            Long cnt = (Long) row.get("cnt");
            if (res != null && resolutionDistribution.containsKey(res)) {
                resolutionDistribution.put(res, cnt.intValue());
            }
        }
        result.put("resolutionDistribution", resolutionDistribution);

        return result;
    }

    @Override
    public List<TaskStatisticsDaily> getDailyTrend(int days) {
        LocalDate startDate = LocalDate.now().minusDays(days - 1);
        LocalDate endDate = LocalDate.now();

        LambdaQueryWrapper<TaskStatisticsDaily> wrapper = new LambdaQueryWrapper<TaskStatisticsDaily>()
                .between(TaskStatisticsDaily::getStatDate, startDate, endDate)
                .orderByAsc(TaskStatisticsDaily::getStatDate);

        return statisticsMapper.selectList(wrapper);
    }

    @Override
    public Map<String, Object> getStepTimeStats() {
        Map<String, Object> result = new HashMap<>();

        LocalDate today = LocalDate.now();
        TaskStatisticsDaily todayStats = getOrInitStats(today);

        Map<String, Integer> stepTimes = new HashMap<>();
        stepTimes.put("summary", todayStats.getAvgSummaryTime() != null ? todayStats.getAvgSummaryTime() : 0);
        stepTimes.put("storyboard", todayStats.getAvgStoryboardTime() != null ? todayStats.getAvgStoryboardTime() : 0);
        stepTimes.put("asset", todayStats.getAvgAssetTime() != null ? todayStats.getAvgAssetTime() : 0);
        stepTimes.put("image", todayStats.getAvgImageTime() != null ? todayStats.getAvgImageTime() : 0);
        stepTimes.put("audio", todayStats.getAvgAudioTime() != null ? todayStats.getAvgAudioTime() : 0);
        stepTimes.put("video", todayStats.getAvgVideoTime() != null ? todayStats.getAvgVideoTime() : 0);

        result.put("stepTimes", stepTimes);

        return result;
    }

    private TaskStatisticsDaily getOrInitStats(LocalDate date) {
        LambdaQueryWrapper<TaskStatisticsDaily> wrapper = new LambdaQueryWrapper<TaskStatisticsDaily>()
                .eq(TaskStatisticsDaily::getStatDate, date);
        TaskStatisticsDaily stats = statisticsMapper.selectOne(wrapper);
        if (stats == null) {
            stats = new TaskStatisticsDaily();
            stats.setStatDate(date);
            stats.setTotalTaskCount(0);
            stats.setSuccessCount(0);
            stats.setFailureCount(0);
            stats.setSuccessRate(BigDecimal.ZERO);
            stats.setFailureRate(BigDecimal.ZERO);
            stats.setAvgSummaryTime(0);
            stats.setAvgStoryboardTime(0);
            stats.setAvgAssetTime(0);
            stats.setAvgImageTime(0);
            stats.setAvgAudioTime(0);
            stats.setAvgVideoTime(0);
            stats.setAvgTotalTime(0);
            stats.setNewUserCount(0);
            stats.setActiveUserCount(0);
            stats.setDiskUsageBytes(0L);
        }
        return stats;
    }
}
