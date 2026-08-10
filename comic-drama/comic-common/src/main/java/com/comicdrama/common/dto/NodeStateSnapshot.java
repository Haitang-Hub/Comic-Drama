package com.comicdrama.common.dto;

import java.time.LocalDateTime;

/**
 * 节点状态快照 DTO（跨模块共享）。
 * 用于在 workflow-service 和 task-service 之间传递节点状态信息。
 */
public record NodeStateSnapshot(
        Long taskId,
        Integer step,
        String nodeType,
        String nodeKey,
        String nodeName,
        Integer nodeStatus,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Long durationMs,
        String inputSnapshot,
        String outputSnapshot,
        String errorMsg
) {
}
