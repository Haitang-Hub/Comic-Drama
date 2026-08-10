package com.comicdrama.workflow.handler;

/**
 * 任务失败记录器。
 * 将步骤执行异常写入 task_failure_log 表，供断点续跑和运维排查使用。
 * Phase-2 实现：调用 task-service 的 REST API 或直接操作数据库。
 */
public interface TaskFailureRecorder {

    /**
     * 记录失败日志。
     *
     * @param taskId    任务 ID
     * @param step      步骤顺序（1-8）
     * @param nodeKey   节点标识（如 storyboard_3_image）
     * @param errorMsg  错误信息
     * @param throwable 异常对象
     */
    void record(Long taskId, Integer step, String nodeKey, String errorMsg, Throwable throwable);
}