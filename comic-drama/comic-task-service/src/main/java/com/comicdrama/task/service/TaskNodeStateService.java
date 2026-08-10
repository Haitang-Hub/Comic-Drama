package com.comicdrama.task.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.comicdrama.task.entity.TaskNodeState;

import java.time.LocalDateTime;
import java.util.List;

public interface TaskNodeStateService extends IService<TaskNodeState> {

    List<TaskNodeState> listByTaskId(Long taskId);

    TaskNodeState getByStep(Long taskId, Integer stepOrder);

    void saveNodeState(Long taskId, Integer step, Integer status,
                       LocalDateTime startTime, LocalDateTime endTime, Long duration,
                       String input, String output);

    void resetNodeStatesFrom(Long taskId, Integer fromStep);

    void resetNodeState(Long taskId, Integer step);
}
