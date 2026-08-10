package com.comicdrama.task.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.comicdrama.task.entity.TaskNodeState;
import com.comicdrama.task.mapper.TaskNodeStateMapper;
import com.comicdrama.task.service.TaskNodeStateService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class TaskNodeStateServiceImpl extends ServiceImpl<TaskNodeStateMapper, TaskNodeState> implements TaskNodeStateService {

    @Override
    public List<TaskNodeState> listByTaskId(Long taskId) {
        return this.list(new LambdaQueryWrapper<TaskNodeState>()
                .eq(TaskNodeState::getTaskId, taskId)
                .orderByAsc(TaskNodeState::getStep));
    }

    @Override
    public TaskNodeState getByStep(Long taskId, Integer stepOrder) {
        return this.getOne(new LambdaQueryWrapper<TaskNodeState>()
                .eq(TaskNodeState::getTaskId, taskId)
                .eq(TaskNodeState::getStep, stepOrder)
                .last("LIMIT 1"));
    }

    @Override
    public void saveNodeState(Long taskId, Integer step, Integer status,
                              LocalDateTime startTime, LocalDateTime endTime, Long duration,
                              String input, String output) {
        TaskNodeState existing = getByStep(taskId, step);

        if (existing != null) {
            existing.setNodeStatus(status);
            existing.setStartTime(startTime);
            existing.setEndTime(endTime);
            existing.setDurationMs(duration);
            existing.setInputSnapshot(input);
            existing.setOutputSnapshot(output);
            existing.setLastRegenerateTime(LocalDateTime.now());
            if (status == 3) {
                existing.setErrorMsg(output);
            } else {
                existing.setErrorMsg(null);
            }
            if (existing.getRegenerateCount() == null) {
                existing.setRegenerateCount(0);
            }
            existing.setRegenerateCount(existing.getRegenerateCount() + 1);
            existing.setRetryCount(0);
            this.updateById(existing);
        } else {
            TaskNodeState node = new TaskNodeState();
            node.setTaskId(taskId);
            node.setStep(step);
            node.setNodeStatus(status);
            node.setStartTime(startTime);
            node.setEndTime(endTime);
            node.setDurationMs(duration);
            node.setInputSnapshot(input);
            node.setOutputSnapshot(output);
            node.setRetryCount(0);
            node.setRegenerateCount(0);
            node.setCanRegenerate(1);
            node.setLastRegenerateTime(LocalDateTime.now());
            if (status == 3) {
                node.setErrorMsg(output);
            }
            this.save(node);
        }
    }

    @Override
    public void resetNodeStatesFrom(Long taskId, Integer fromStep) {
        this.update(new LambdaUpdateWrapper<TaskNodeState>()
                .eq(TaskNodeState::getTaskId, taskId)
                .ge(TaskNodeState::getStep, fromStep)
                .set(TaskNodeState::getNodeStatus, 0)
                .set(TaskNodeState::getStartTime, null)
                .set(TaskNodeState::getEndTime, null)
                .set(TaskNodeState::getDurationMs, null)
                .set(TaskNodeState::getOutputSnapshot, null)
                .set(TaskNodeState::getErrorMsg, null)
                .set(TaskNodeState::getRetryCount, 0));
    }

    @Override
    public void resetNodeState(Long taskId, Integer step) {
        this.update(new LambdaUpdateWrapper<TaskNodeState>()
                .eq(TaskNodeState::getTaskId, taskId)
                .eq(TaskNodeState::getStep, step)
                .set(TaskNodeState::getNodeStatus, 0)
                .set(TaskNodeState::getStartTime, null)
                .set(TaskNodeState::getEndTime, null)
                .set(TaskNodeState::getDurationMs, null)
                .set(TaskNodeState::getOutputSnapshot, null)
                .set(TaskNodeState::getErrorMsg, null)
                .set(TaskNodeState::getRetryCount, 0));
    }
}
