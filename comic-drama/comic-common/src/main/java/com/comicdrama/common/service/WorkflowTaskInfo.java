package com.comicdrama.common.service;

import com.comicdrama.common.dto.TaskCreateDTO;

/**
 * 任务信息 DTO，用于在服务间传递任务上下文。
 */
public record WorkflowTaskInfo(Long taskId, String taskNo, Long userId, String title,
                               TaskCreateDTO requestDTO) {
}