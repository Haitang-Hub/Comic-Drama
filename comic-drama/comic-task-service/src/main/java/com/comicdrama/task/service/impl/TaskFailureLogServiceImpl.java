package com.comicdrama.task.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.comicdrama.common.dto.PageQuery;
import com.comicdrama.common.result.PageResult;
import com.comicdrama.task.entity.TaskFailureLog;
import com.comicdrama.task.mapper.TaskFailureLogMapper;
import com.comicdrama.task.service.TaskFailureLogService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TaskFailureLogServiceImpl extends ServiceImpl<TaskFailureLogMapper, TaskFailureLog> implements TaskFailureLogService {

    @Override
    public PageResult<TaskFailureLog> page(PageQuery query, Long taskId) {
        LambdaQueryWrapper<TaskFailureLog> wrapper = new LambdaQueryWrapper<>();
        if (taskId != null) {
            wrapper.eq(TaskFailureLog::getTaskId, taskId);
        }
        wrapper.orderByDesc(TaskFailureLog::getCreateTime);
        Page<TaskFailureLog> page = new Page<>(query.getPage(), query.getSize());
        Page<TaskFailureLog> result = this.page(page, wrapper);
        return new PageResult<>(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public List<TaskFailureLog> listByTaskId(Long taskId) {
        LambdaQueryWrapper<TaskFailureLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TaskFailureLog::getTaskId, taskId)
               .orderByDesc(TaskFailureLog::getCreateTime);
        return this.list(wrapper);
    }

    @Override
    public void deleteByTaskId(Long taskId) {
        LambdaQueryWrapper<TaskFailureLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TaskFailureLog::getTaskId, taskId);
        this.remove(wrapper);
    }
}
