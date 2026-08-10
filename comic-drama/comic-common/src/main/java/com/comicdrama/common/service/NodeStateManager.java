package com.comicdrama.common.service;

import com.comicdrama.common.dto.NodeStateSnapshot;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 节点状态管理器接口（Phase-3）。
 * 负责读写 task_node_state 表，支撑节点重生成和断点续跑。
 */
public interface NodeStateManager {

    /**
     * 保存节点状态。
     */
    void saveNodeState(Long taskId, Integer step, Integer status,
                       LocalDateTime startTime, LocalDateTime endTime,
                       Long durationMs, String inputSnapshot, String outputSnapshot);

    /**
     * 获取指定任务所有节点状态。
     */
    List<NodeStateSnapshot> listNodeStates(Long taskId);

    /**
     * 获取指定步骤的节点状态。
     */
    NodeStateSnapshot getNodeState(Long taskId, Integer stepOrder);

    /**
     * 重置从指定步骤开始的所有节点状态。
     */
    void resetNodeStatesFrom(Long taskId, Integer fromStep);

    /**
     * 重置单个步骤的节点状态。
     */
    void resetNodeState(Long taskId, Integer step);

    /**
     * 查找最近失败的步骤顺序。
     *
     * @return 失败步骤顺序，若无失败节点返回 null
     */
    Integer findLatestFailedStep(Long taskId);

    /**
     * 查找断点续跑的起始步骤：返回第一个未完成的步骤（进行中=1 或 失败=3）。
     * 若所有步骤都已完成（状态=2），返回 null。
     *
     * @return 续跑起始步骤顺序，若无未完成节点返回 null
     */
    Integer findResumeStep(Long taskId);
}