package com.comicdrama.task.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.comicdrama.common.dto.PageQuery;
import com.comicdrama.common.result.PageResult;
import com.comicdrama.task.entity.TaskStatisticsDaily;
import com.comicdrama.task.mapper.TaskStatisticsDailyMapper;
import com.comicdrama.task.service.TaskStatisticsDailyService;
import org.springframework.stereotype.Service;

@Service
public class TaskStatisticsDailyServiceImpl extends ServiceImpl<TaskStatisticsDailyMapper, TaskStatisticsDaily> implements TaskStatisticsDailyService {

    @Override
    public PageResult<TaskStatisticsDaily> page(PageQuery query) {
        LambdaQueryWrapper<TaskStatisticsDaily> wrapper = new LambdaQueryWrapper<TaskStatisticsDaily>()
                .orderByDesc(TaskStatisticsDaily::getStatDate);
        Page<TaskStatisticsDaily> page = new Page<>(query.getPage(), query.getSize());
        Page<TaskStatisticsDaily> result = this.page(page, wrapper);
        return new PageResult<>(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }
}
