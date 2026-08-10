package com.comicdrama.task.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.comicdrama.common.dto.PageQuery;
import com.comicdrama.common.result.PageResult;
import com.comicdrama.task.entity.TaskFailureLog;

import java.util.List;

public interface TaskFailureLogService extends IService<TaskFailureLog> {
    PageResult<TaskFailureLog> page(PageQuery query, Long taskId);

    List<TaskFailureLog> listByTaskId(Long taskId);

    void deleteByTaskId(Long taskId);
}
