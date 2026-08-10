package com.comicdrama.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.comicdrama.common.dto.PageQuery;
import com.comicdrama.common.result.PageResult;
import com.comicdrama.system.entity.TaskStatisticsDaily;

/**
 * 任务每日统计服务（Phase-1 只读，Phase-4 由定时任务聚合写入）
 */
public interface TaskStatisticsDailyService extends IService<TaskStatisticsDaily> {

    PageResult<TaskStatisticsDaily> page(PageQuery query);
}
