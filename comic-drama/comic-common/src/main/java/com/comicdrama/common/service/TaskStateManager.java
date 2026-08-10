package com.comicdrama.common.service;

import java.time.LocalDateTime;

/**
 * 任务状态管理器接口。
 * 负责更新 comic_task 表的状态字段。
 */
public interface TaskStateManager {

    /**
     * 标记任务为「生成中」。
     */
    void markAsRunning(Long taskId, int currentStep, LocalDateTime startTime);

    /**
     * 更新任务的当前步骤和进度（实时更新到 comic_task 表）。
     */
    void updateStepProgress(Long taskId, int currentStep, int progress, int totalProgress);

    /**
     * 标记任务为「已完成」。
     */
    void markAsDone(Long taskId, int progress, int totalConsumeTime,
                    String coverUrl, String finalVideoUrl, LocalDateTime endTime);

    /**
     * 标记任务为「已完成」（支持指定完成的步骤）。
     */
    default void markAsDone(Long taskId, int progress, int totalConsumeTime,
                            String coverUrl, String finalVideoUrl, LocalDateTime endTime,
                            int completedStep) {
        markAsDone(taskId, progress, totalConsumeTime, coverUrl, finalVideoUrl, endTime);
    }

    /**
     * 标记任务为「失败」。
     */
    void markAsFailed(Long taskId, int failureStep, String failureReason,
                      String failureDetail, LocalDateTime endTime);

    /**
     * 标记任务为「已暂停」。
     */
    void markAsPaused(Long taskId, int pausedStep, LocalDateTime pauseTime);

    /**
     * 设置/清除计划暂停标记（用于「完成此阶段」语义：等当前步骤执行完毕后自动暂停）。
     * @param taskId             任务ID
     * @param plannedPauseFlag   true=设置标记（当前步执行完后停）；false=清除标记
     * @param expireMinutes      标记的最大有效期（分钟），超时自动失效，避免无限阻塞
     */
    default void setPlannedPause(Long taskId, boolean plannedPauseFlag, int expireMinutes) {}

    /**
     * 当前是否存在计划暂停标记（workflow-service 每步执行后检查）。
     */
    default boolean isPlannedPause(Long taskId) { return false; }

    /**
     * 显式清除计划暂停标记。
     */
    default void clearPlannedPause(Long taskId) {}

    /**
     * 获取任务当前状态（缓存优先，DB兜底）。
     * 返回值对齐 TaskStatus.code：0排队 1生成中 2已完成 3失败 4已暂停。
     * 若任务不存在返回 -1。
     */
    default int getStatus(Long taskId) {
        return -1;
    }
}