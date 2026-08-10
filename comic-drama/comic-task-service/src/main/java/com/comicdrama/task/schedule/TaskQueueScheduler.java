package com.comicdrama.task.schedule;

import com.comicdrama.common.enums.TaskStatus;
import com.comicdrama.common.queue.TaskQueueEntry;
import com.comicdrama.task.entity.ComicTask;
import com.comicdrama.task.mapper.ComicTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 任务队列调度器（Phase-2）。
 *
 * <p>每 poll-interval-ms 从内存队列拉取一个任务，委托给 {@link TaskPipelineRunner} 执行真实的 7 步 AI 流水线。</p>
 *
 * <h3>执行流程</h3>
 * <ol>
 *   <li>从内存队列取出队首任务</li>
 *   <li>校验任务状态（已暂停的任务跳过）</li>
 *   <li>调用 {@link TaskPipelineRunner#runTask(Long)} 执行流水线</li>
 *   <li>TaskPipelineRunner 内部负责状态管理、进度更新和异常处理</li>
 * </ol>
 *
 * <h3>Phase 演进</h3>
 * <ul>
 *   <li>Phase-1: stub 实现，仅打通链路</li>
 *   <li>Phase-2: 调用 TaskPipelineRunner 执行 7 步 AI 流水线</li>
 *   <li>Phase-5: 多节点部署时需添加分布式锁</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskQueueScheduler {

    private final com.comicdrama.common.queue.TaskQueue taskQueue;
    private final ComicTaskMapper comicTaskMapper;
    private final TaskPipelineRunner taskPipelineRunner;

    /**
     * 固定延迟轮询（上次执行完成后等待 pollIntervalMs 再触发）。
     * Phase-2 单节点；Phase-5 多节点需分布式锁。
     */
    @Scheduled(fixedDelayString = "${task.queue.poll-interval-ms:1000}")
    public void consume() {
        TaskQueueEntry entry = taskQueue.dequeue();
        if (entry == null) {
            return;
        }
        Long taskId = entry.getTaskId();
        ComicTask task = comicTaskMapper.selectById(taskId);
        if (task == null) {
            log.warn("队列消费：任务不存在 taskId={}", taskId);
            return;
        }
        Integer st = task.getStatus();
        if (st != null && (st == TaskStatus.PAUSED.getCode())) {
            log.info("队列消费：任务已暂停，跳过 taskId={}", taskId);
            return;
        }

        log.info("队列消费：开始执行任务 taskId={}", taskId);
        taskPipelineRunner.runTask(taskId);
    }
}
