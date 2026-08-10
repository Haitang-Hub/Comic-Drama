package com.comicdrama.common.queue;

import java.util.List;

/**
 * 任务队列抽象（轻量中间件）。
 * Phase-1 实现 {@link InMemoryTaskQueue}（PriorityBlockingQueue），
 * Phase-5 替换为 RocketMQ 实现，业务代码不感知。
 */
public interface TaskQueue {

    /** 入队 */
    boolean enqueue(TaskQueueEntry entry);

    /**
     * 取出队首（阻塞/非阻塞由实现决定）。
     * Phase-1 内存实现采用非阻塞 poll。
     */
    TaskQueueEntry dequeue();

    /** 查看队首但不移除 */
    TaskQueueEntry peek();

    /** 当前队列长度 */
    int size();

    /** 指定任务在队列中的位置（从 1 开始，0 表示不在队列） */
    int getPosition(Long taskId);

    /** 移除指定任务 */
    boolean remove(Long taskId);

    /** 当前等待中的全部条目（按优先级+入队时间排序，仅用于查看） */
    List<TaskQueueEntry> waitingList();
}
