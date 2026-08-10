package com.comicdrama.task.service.impl;

import com.comicdrama.common.dto.NodeStateSnapshot;
import com.comicdrama.common.service.NodeStateManager;
import com.comicdrama.task.entity.TaskNodeState;
import com.comicdrama.task.service.TaskNodeStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 节点状态管理器实现类（Phase-3）。
 * 桥接 comic-task-service 与 comic-workflow-service，
 * 实现 WorkflowPipelineService.NodeStateManager 回调接口。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NodeStateManagerImpl implements NodeStateManager {

    private final TaskNodeStateService taskNodeStateService;

    @Override
    public void saveNodeState(Long taskId, Integer step, Integer status,
                              LocalDateTime startTime, LocalDateTime endTime,
                              Long durationMs, String inputSnapshot, String outputSnapshot) {
        taskNodeStateService.saveNodeState(taskId, step, status, startTime, endTime,
                durationMs, inputSnapshot, outputSnapshot);
    }

    @Override
    public List<NodeStateSnapshot> listNodeStates(Long taskId) {
        return taskNodeStateService.listByTaskId(taskId).stream()
                .map(this::toSnapshot)
                .collect(Collectors.toList());
    }

    @Override
    public NodeStateSnapshot getNodeState(Long taskId, Integer stepOrder) {
        TaskNodeState state = taskNodeStateService.getByStep(taskId, stepOrder);
        return state != null ? toSnapshot(state) : null;
    }

    @Override
    public void resetNodeStatesFrom(Long taskId, Integer fromStep) {
        taskNodeStateService.resetNodeStatesFrom(taskId, fromStep);
    }

    @Override
    public void resetNodeState(Long taskId, Integer step) {
        taskNodeStateService.resetNodeState(taskId, step);
    }

    @Override
    public Integer findLatestFailedStep(Long taskId) {
        List<TaskNodeState> allStates = taskNodeStateService.listByTaskId(taskId);
        return allStates.stream()
                .filter(s -> s.getNodeStatus() != null && s.getNodeStatus() == 3)
                .map(TaskNodeState::getStep)
                .max(Integer::compareTo)
                .orElse(null);
    }

    @Override
    public Integer findResumeStep(Long taskId) {
        List<TaskNodeState> allStates = taskNodeStateService.listByTaskId(taskId);
        return allStates.stream()
                .filter(s -> s.getNodeStatus() != null
                        && (s.getNodeStatus() == 1 || s.getNodeStatus() == 3))
                .map(TaskNodeState::getStep)
                .min(Integer::compareTo)
                .orElse(null);
    }

    private NodeStateSnapshot toSnapshot(TaskNodeState state) {
        return new NodeStateSnapshot(
                state.getTaskId(),
                state.getStep(),
                state.getNodeType(),
                state.getNodeKey(),
                state.getNodeName(),
                state.getNodeStatus(),
                state.getStartTime(),
                state.getEndTime(),
                state.getDurationMs(),
                state.getInputSnapshot(),
                state.getOutputSnapshot(),
                state.getErrorMsg()
        );
    }
}
