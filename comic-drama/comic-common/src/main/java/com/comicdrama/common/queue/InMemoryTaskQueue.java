package com.comicdrama.common.queue;

import com.comicdrama.common.constant.QueueConstants;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 内存任务队列实现（Phase-1 轻量中间件）。
 * 基于 PriorityBlockingQueue，按 (priority ASC, enqueuedTime ASC) 排序。
 * Phase-5 将被 RocketMQ 实现替换。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "task.queue.type", havingValue = "memory", matchIfMissing = true)
public class InMemoryTaskQueue implements TaskQueue {

    private final PriorityBlockingQueue<TaskQueueEntry> queue =
            new PriorityBlockingQueue<>(QueueConstants.QUEUE_CAPACITY,
                    Comparator.comparingInt((TaskQueueEntry e) -> e.getPriority() == null ? QueueConstants.DEFAULT_PRIORITY : e.getPriority())
                            .thenComparing(e -> e.getEnqueuedTime() == null ? LocalDateTime.now() : e.getEnqueuedTime()));

    private final ReentrantLock lock = new ReentrantLock();

    @PostConstruct
    public void init() {
        log.info("InMemoryTaskQueue 初始化完成（轻量实现，Phase-5 替换为 RocketMQ）");
    }

    @Override
    public boolean enqueue(TaskQueueEntry entry) {
        if (entry == null || entry.getTaskId() == null) {
            return false;
        }
        if (entry.getPriority() == null) {
            entry.setPriority(QueueConstants.DEFAULT_PRIORITY);
        }
        if (entry.getEnqueuedTime() == null) {
            entry.setEnqueuedTime(LocalDateTime.now());
        }
        boolean ok = queue.offer(entry);
        if (ok) {
            refreshPositions();
            log.debug("任务入队 taskId={}, priority={}, 当前队列长度={}", entry.getTaskId(), entry.getPriority(), queue.size());
        }
        return ok;
    }

    @Override
    public TaskQueueEntry dequeue() {
        TaskQueueEntry entry = queue.poll();
        if (entry != null) {
            refreshPositions();
        }
        return entry;
    }

    @Override
    public TaskQueueEntry peek() {
        return queue.peek();
    }

    @Override
    public int size() {
        return queue.size();
    }

    @Override
    public int getPosition(Long taskId) {
        if (taskId == null) {
            return 0;
        }
        List<TaskQueueEntry> sorted = sortedSnapshot();
        for (int i = 0; i < sorted.size(); i++) {
            if (taskId.equals(sorted.get(i).getTaskId())) {
                return i + 1;
            }
        }
        return 0;
    }

    @Override
    public boolean remove(Long taskId) {
        if (taskId == null) {
            return false;
        }
        boolean removed = queue.removeIf(e -> taskId.equals(e.getTaskId()));
        if (removed) {
            refreshPositions();
        }
        return removed;
    }

    @Override
    public List<TaskQueueEntry> waitingList() {
        return sortedSnapshot();
    }

    private List<TaskQueueEntry> sortedSnapshot() {
        lock.lock();
        try {
            return new ArrayList<>(queue);
        } finally {
            lock.unlock();
        }
    }

    private void refreshPositions() {
        lock.lock();
        try {
            List<TaskQueueEntry> sorted = new ArrayList<>(queue);
            sorted.sort(queue.comparator());
            for (int i = 0; i < sorted.size(); i++) {
                sorted.get(i).setQueuePosition(i + 1);
            }
        } finally {
            lock.unlock();
        }
    }
}
