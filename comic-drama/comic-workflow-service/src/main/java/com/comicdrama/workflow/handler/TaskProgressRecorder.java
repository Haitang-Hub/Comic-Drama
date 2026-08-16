package com.comicdrama.workflow.handler;

/**
 * 任务进度记录器。
 * 将步骤执行进度写入 task_progress_log 表并广播 WebSocket 事件。
 */
public interface TaskProgressRecorder {

    /**
     * 记录并推送进度（精简接口，不传批量进度的子项信息时子项 null）。
     *
     * @param taskId        任务 ID
     * @param step          步骤顺序（1-9）
     * @param progress      步骤内进度（0-100）
     * @param totalProgress 全局总进度（0-100，基于步骤均等权重计算）
     * @param message       进度描述信息（建议精炼，4-12字）
     */
    default void record(Long taskId, Integer step, Integer progress, Integer totalProgress, String message) {
        record(taskId, step, null, progress, totalProgress, message, null, null);
    }

    /**
     * 记录并推送进度（结构化版，带批量子项计数 & 人类可读步骤名）。
     *
     * @param taskId        任务 ID
     * @param step          步骤序号（1-9）
     * @param stepName      步骤中文名（可为 null，网关/前端会按 step 补默认名）
     * @param progress      步骤内进度（0-100）
     * @param totalProgress 全局总进度（0-100）
     * @param message       精炼进度文案（如"生成中"、"已完成 2/5"）——若传 null，后端可自动依据 itemDone/itemTotal 合成
     * @param itemDone      当前步骤已完成子项数（批量步骤用，可 null）
     * @param itemTotal     当前步骤总子项数（批量步骤用，可 null）
     */
    void record(Long taskId, Integer step, String stepName,
                Integer progress, Integer totalProgress, String message,
                Integer itemDone, Integer itemTotal);
}