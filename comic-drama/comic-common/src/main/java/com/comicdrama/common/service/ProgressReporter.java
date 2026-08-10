package com.comicdrama.common.service;

/**
 * 进度报告器接口。
 * 负责将步骤执行进度写入 task_progress_log 表并广播事件。
 */
public interface ProgressReporter {

    /**
     * 记录并推送进度。
     *
     * @param taskId        任务 ID
     * @param step          步骤顺序（1-7）
     * @param progress      步骤内进度（0-100）
     * @param totalProgress 流水线总进度（0-100）
     * @param message       进度描述信息
     */
    void reportProgress(Long taskId, Integer step, Integer progress,
                        Integer totalProgress, String message);
}