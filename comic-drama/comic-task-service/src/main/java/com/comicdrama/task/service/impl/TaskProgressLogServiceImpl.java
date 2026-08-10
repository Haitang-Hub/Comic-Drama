package com.comicdrama.task.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.comicdrama.task.entity.TaskProgressLog;
import com.comicdrama.task.mapper.TaskProgressLogMapper;
import com.comicdrama.task.service.TaskProgressLogService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TaskProgressLogServiceImpl extends ServiceImpl<TaskProgressLogMapper, TaskProgressLog> implements TaskProgressLogService {

    @Override
    public List<TaskProgressLog> listByTaskId(Long taskId) {
        return this.list(new LambdaQueryWrapper<TaskProgressLog>()
                .eq(TaskProgressLog::getTaskId, taskId)
                .orderByAsc(TaskProgressLog::getCreateTime));
    }

    @Override
    public void removeByTaskId(Long taskId) {
        this.remove(new LambdaQueryWrapper<TaskProgressLog>()
                .eq(TaskProgressLog::getTaskId, taskId));
    }
}
