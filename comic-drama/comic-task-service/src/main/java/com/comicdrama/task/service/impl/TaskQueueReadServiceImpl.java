package com.comicdrama.task.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.comicdrama.common.dto.PageQuery;
import com.comicdrama.common.result.PageResult;
import com.comicdrama.task.entity.TaskQueue;
import com.comicdrama.task.mapper.TaskQueueMapper;
import com.comicdrama.task.service.TaskQueueReadService;
import org.springframework.stereotype.Service;

@Service
public class TaskQueueReadServiceImpl extends ServiceImpl<TaskQueueMapper, TaskQueue> implements TaskQueueReadService {

    @Override
    public PageResult<TaskQueue> page(PageQuery query, Integer queueStatus) {
        LambdaQueryWrapper<TaskQueue> wrapper = new LambdaQueryWrapper<>();
        if (queueStatus != null) {
            wrapper.eq(TaskQueue::getQueueStatus, queueStatus);
        }
        wrapper.orderByAsc(TaskQueue::getPriority).orderByAsc(TaskQueue::getEnqueuedTime);
        Page<TaskQueue> page = new Page<>(query.getPage(), query.getSize());
        Page<TaskQueue> result = this.page(page, wrapper);
        return new PageResult<>(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }
}
