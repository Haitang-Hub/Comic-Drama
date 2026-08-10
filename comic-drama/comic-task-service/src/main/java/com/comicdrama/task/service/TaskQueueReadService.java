package com.comicdrama.task.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.comicdrama.common.dto.PageQuery;
import com.comicdrama.common.result.PageResult;
import com.comicdrama.task.entity.TaskQueue;

public interface TaskQueueReadService extends IService<TaskQueue> {
    PageResult<TaskQueue> page(PageQuery query, Integer queueStatus);
}
