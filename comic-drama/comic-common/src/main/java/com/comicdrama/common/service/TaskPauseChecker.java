package com.comicdrama.common.service;

/**
 * 任务暂停检查器接口。
 * 步骤处理器在批量执行每个产物前应调用此接口检查任务是否已被暂停。
 * 若返回 true，处理器应立即保存当前进度并抛出 TaskPausedException 终止执行。
 */
public interface TaskPauseChecker {

    /**
     * 检查指定任务是否已被暂停。
     *
     * @param taskId 任务 ID
     * @return true 表示已暂停，应立即停止执行
     */
    boolean isPaused(Long taskId);
}
