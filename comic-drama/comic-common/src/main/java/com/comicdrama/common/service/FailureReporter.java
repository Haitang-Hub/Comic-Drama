package com.comicdrama.common.service;

/**
 * 失败报告器接口。
 * 负责将步骤执行异常写入 task_failure_log 表。
 */
public interface FailureReporter {

    /**
     * 记录失败日志。
     *
     * @param taskId    任务 ID
     * @param step      步骤顺序（1-8）
     * @param stepCode  步骤编码（如 storyboard）
     * @param errorMsg  错误信息
     * @param throwable 异常对象
     */
    void reportFailure(Long taskId, Integer step, String stepCode,
                       String errorMsg, Throwable throwable);
}