package com.comicdrama.common.service;

import com.comicdrama.common.dto.TaskCreateDTO;

/**
 * 任务信息提供者接口。
 * 负责获取任务的基本信息用于构建 StepContext。
 */
public interface TaskInfoProvider {

    /**
     * 根据 taskId 获取任务信息。
     *
     * @param taskId 任务 ID
     * @return 任务信息
     */
    WorkflowTaskInfo getTaskInfo(Long taskId);

    /**
     * 注册/更新任务信息到缓存中。
     *
     * @param taskId    任务 ID
     * @param userId    用户 ID
     * @param title     任务标题
     * @param dto       任务创建 DTO（含 artStyle/visualStyle 等参数）
     */
    void registerTask(Long taskId, Long userId, String title, TaskCreateDTO dto);
}