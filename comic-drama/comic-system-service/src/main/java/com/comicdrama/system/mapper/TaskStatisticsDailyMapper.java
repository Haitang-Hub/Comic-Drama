package com.comicdrama.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.comicdrama.system.entity.TaskStatisticsDaily;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface TaskStatisticsDailyMapper extends BaseMapper<TaskStatisticsDaily> {

    /**
     * 按日期范围查询统计数据。
     */
    @Select("SELECT * FROM task_statistics_daily WHERE stat_date BETWEEN #{startDate} AND #{endDate} ORDER BY stat_date ASC")
    List<TaskStatisticsDaily> selectByDateRange(@Param("startDate") LocalDate startDate,
                                                 @Param("endDate") LocalDate endDate);

    /**
     * 汇总指定日期范围内的统计数据。
     */
    @Select("SELECT " +
            "COALESCE(SUM(total_task_count), 0) as totalTasks, " +
            "COALESCE(SUM(success_count), 0) as totalSuccess, " +
            "COALESCE(SUM(failure_count), 0) as totalFailure, " +
            "COALESCE(AVG(success_rate), 0) as avgSuccessRate, " +
            "COALESCE(AVG(failure_rate), 0) as avgFailureRate, " +
            "COALESCE(AVG(avg_summary_time), 0) as avgSummaryTime, " +
            "COALESCE(AVG(avg_storyboard_time), 0) as avgStoryboardTime, " +
            "COALESCE(AVG(avg_asset_time), 0) as avgAssetTime, " +
            "COALESCE(AVG(avg_image_time), 0) as avgImageTime, " +
            "COALESCE(AVG(avg_audio_time), 0) as avgAudioTime, " +
            "COALESCE(AVG(avg_video_time), 0) as avgVideoTime, " +
            "COALESCE(AVG(avg_total_time), 0) as avgTotalTime " +
            "FROM task_statistics_daily WHERE stat_date BETWEEN #{startDate} AND #{endDate}")
    Map<String, Object> aggregateByDateRange(@Param("startDate") LocalDate startDate,
                                              @Param("endDate") LocalDate endDate);

    /**
     * 查询总作品数。
     */
    @Select("SELECT COUNT(*) FROM task_statistics_daily")
    long countTotalWorks();
}
