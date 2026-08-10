package com.comicdrama.task.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.comicdrama.task.entity.TaskProgressLog;

import java.util.List;

public interface TaskProgressLogService extends IService<TaskProgressLog> {
    List<TaskProgressLog> listByTaskId(Long taskId);

    void removeByTaskId(Long taskId);
}
