package com.comicdrama.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.comicdrama.common.entity.BaseTimeEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 任务每日统计表（数据看板：按天聚合，加速查询）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("task_statistics_daily")
public class TaskStatisticsDaily extends BaseTimeEntity {

    private LocalDate statDate;

    private Integer totalTaskCount;

    private Integer successCount;

    private Integer failureCount;

    private BigDecimal successRate;

    private BigDecimal failureRate;

    /** 平均摘要耗时（秒） */
    private Integer avgSummaryTime;

    /** 平均分镜耗时（秒） */
    private Integer avgStoryboardTime;

    /** 平均资产设计耗时（秒） */
    private Integer avgAssetTime;

    /** 平均图像生成耗时（秒） */
    private Integer avgImageTime;

    /** 平均音频生成耗时（秒） */
    private Integer avgAudioTime;

    /** 平均视频生成耗时（秒） */
    private Integer avgVideoTime;

    /** 平均总耗时（秒） */
    private Integer avgTotalTime;

    private Integer newUserCount;

    private Integer activeUserCount;

    private Long diskUsageBytes;
}
