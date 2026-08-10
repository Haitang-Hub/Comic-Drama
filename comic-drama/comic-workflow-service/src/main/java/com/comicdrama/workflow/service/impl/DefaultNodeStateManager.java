package com.comicdrama.workflow.service.impl;

import com.comicdrama.common.dto.NodeStateSnapshot;
import com.comicdrama.common.service.NodeStateManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 默认节点状态管理器实现（Phase-4：同时持久化到数据库 + 内存缓存）。
 */
@Slf4j
@Component
public class DefaultNodeStateManager implements NodeStateManager {

    private final JdbcTemplate jdbcTemplate;

    private final Map<Long, List<NodeStateSnapshot>> nodeStates = new ConcurrentHashMap<>();

    public DefaultNodeStateManager(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void saveNodeState(Long taskId, Integer step, Integer status,
                              LocalDateTime startTime, LocalDateTime endTime,
                              Long durationMs, String inputSnapshot, String outputSnapshot) {
        List<NodeStateSnapshot> states = nodeStates.computeIfAbsent(taskId, k -> new ArrayList<>());
        String nodeKey = "step_" + step;
        states.removeIf(s -> s.step() == step);
        states.add(new NodeStateSnapshot(taskId, step, "step", nodeKey, "步骤" + step,
                status, startTime, endTime, durationMs, inputSnapshot, outputSnapshot, null));
        log.debug("saveNodeState taskId={}, step={}, status={}", taskId, step, status);
        try {
            jdbcTemplate.update(
                    "INSERT INTO task_node_state (task_id, step, node_type, node_key, node_name, node_status, " +
                            "start_time, end_time, duration_ms, input_snapshot, output_snapshot, retry_count) " +
                            "VALUES (?, ?, 'step', ?, ?, ?, ?, ?, ?, ?, ?, 0) " +
                            "ON DUPLICATE KEY UPDATE node_status=VALUES(node_status), " +
                            "start_time=VALUES(start_time), end_time=VALUES(end_time), " +
                            "duration_ms=VALUES(duration_ms), input_snapshot=VALUES(input_snapshot), " +
                            "output_snapshot=VALUES(output_snapshot), retry_count=retry_count+1",
                    taskId, step, nodeKey, "步骤" + step, status,
                    startTime, endTime, durationMs, inputSnapshot, outputSnapshot);
        } catch (Exception e) {
            log.error("saveNodeState DB写入失败 taskId={}, step={}", taskId, step, e);
        }
    }

    @Override
    public List<NodeStateSnapshot> listNodeStates(Long taskId) {
        return nodeStates.getOrDefault(taskId, Collections.emptyList());
    }

    @Override
    public NodeStateSnapshot getNodeState(Long taskId, Integer stepOrder) {
        return nodeStates.getOrDefault(taskId, Collections.emptyList()).stream()
                .filter(s -> s.step() == stepOrder)
                .findFirst()
                .orElse(null);
    }

    @Override
    public void resetNodeStatesFrom(Long taskId, Integer fromStep) {
        List<NodeStateSnapshot> states = nodeStates.get(taskId);
        if (states != null) {
            states.removeIf(s -> s.step() >= fromStep);
            log.info("resetNodeStatesFrom taskId={}, fromStep={}", taskId, fromStep);
        }
        try {
            jdbcTemplate.update(
                    "DELETE FROM task_node_state WHERE task_id=? AND step >= ?",
                    taskId, fromStep);
        } catch (Exception e) {
            log.error("resetNodeStatesFrom DB删除失败 taskId={}", taskId, e);
        }
    }

    @Override
    public void resetNodeState(Long taskId, Integer step) {
        List<NodeStateSnapshot> states = nodeStates.get(taskId);
        if (states != null) {
            states.removeIf(s -> s.step() == step);
            log.info("resetNodeState taskId={}, step={}", taskId, step);
        }
        try {
            jdbcTemplate.update(
                    "DELETE FROM task_node_state WHERE task_id=? AND step = ?",
                    taskId, step);
        } catch (Exception e) {
            log.error("resetNodeState DB删除失败 taskId={}, step={}", taskId, step, e);
        }
    }

    @Override
    public Integer findLatestFailedStep(Long taskId) {
        List<NodeStateSnapshot> states = nodeStates.get(taskId);
        if (states == null) return null;
        return states.stream()
                .filter(s -> s.nodeStatus() == 3)
                .map(NodeStateSnapshot::step)
                .max(Integer::compareTo)
                .orElse(null);
    }

    @Override
    public Integer findResumeStep(Long taskId) {
        List<NodeStateSnapshot> states = nodeStates.get(taskId);
        if (states == null || states.isEmpty()) return null;
        // 查找第一个未完成的步骤：状态为 1（进行中）或 3（失败）
        // 按 step 升序排列，取第一个未完成的
        return states.stream()
                .filter(s -> s.nodeStatus() != null
                        && (s.nodeStatus() == 1 || s.nodeStatus() == 3))
                .map(NodeStateSnapshot::step)
                .min(Integer::compareTo)
                .orElse(null);
    }
}