package com.comicdrama.workflow.handler;

/**
 * 任务进度记录器。
 * 将步骤执行进度写入 task_progress_log 表并广播 WebSocket 事件。
 * Phase-2 实现：调用 task-service 的 REST API 或直接操作数据库。
 */
public interface TaskProgressRecorder {

    /**
     * 记录并推送进度。
     *
     * @param taskId        任务 ID
     * @param step          步骤顺序（1-9）
     * @param progress      步骤内进度（0-100）
     * @param totalProgress 全局总进度（0-100，基于步骤均等权重计算）
     * @param message       进度描述信息
     */
    void record(Long taskId, Integer step, Integer progress, Integer totalProgress, String message);
}