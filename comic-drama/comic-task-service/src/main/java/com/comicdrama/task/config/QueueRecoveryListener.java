package com.comicdrama.task.config;

import com.comicdrama.common.enums.TaskStatus;
import com.comicdrama.common.queue.TaskQueue;
import com.comicdrama.common.queue.TaskQueueEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 启动期内存队列回灌器。
 * <p>Phase-1 使用 InMemoryTaskQueue（内存 PriorityBlockingQueue），
 * task-service 重启后内存队列清空，导致 comic_task 中排队/失败/暂停中的任务"永远不被调度"。
 * 本监听器在 ApplicationReadyEvent 时，联合 task_queue + comic_task 扫出未完成的任务，
 * 重新入队到内存 TaskQueue，保证"重启不丢队"。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueueRecoveryListener {

    private final DataSource dataSource;
    private final TaskQueue taskQueue;

    @EventListener(ApplicationReadyEvent.class)
    public void recover() {
        log.info("[QueueRecovery] 开始回灌内存任务队列...");
        try {
            JdbcTemplate jt = new JdbcTemplate(dataSource);
            // 任务状态：0排队/1生成中/3失败/4暂停，都需要重新入队（调度器会基于任务表中状态做分支处理）
            // queue_status：0等待/3取消/失败；只考虑还没 finished 的项（started_time 可为空，finished_time 必须为空）
            String sql = "SELECT t.id AS task_id, t.user_id, t.status, q.priority, q.enqueued_time, q.queue_status "
                       + "FROM comic_task t "
                       + "LEFT JOIN task_queue q ON q.task_id = t.id "
                       + "WHERE t.deleted = 0 "
                       + "  AND t.status IN (0, 1, 3, 4) "
                       + "  AND (q.id IS NULL OR q.finished_time IS NULL) "
                       + "ORDER BY COALESCE(q.enqueued_time, t.create_time) ASC";
            List<Map<String, Object>> rows = jt.queryForList(sql);
            if (rows == null || rows.isEmpty()) {
                log.info("[QueueRecovery] 未发现待回灌任务");
                return;
            }
            int recovered = 0;
            int skipped = 0;
            for (Map<String, Object> r : rows) {
                Long taskId = ((Number) r.get("task_id")).longValue();
                if (taskQueue.getPosition(taskId) > 0) {
                    skipped++;
                    continue; // 已在队列中（重复回灌保护）
                }
                Long userId = r.get("user_id") == null ? null : ((Number) r.get("user_id")).longValue();
                Integer priority = r.get("priority") == null ? 100 : ((Number) r.get("priority")).intValue();
                LocalDateTime enq = (LocalDateTime) r.get("enqueued_time");
                if (enq == null) enq = LocalDateTime.now();
                TaskQueueEntry entry = TaskQueueEntry.builder()
                        .taskId(taskId)
                        .userId(userId)
                        .priority(priority)
                        .enqueuedTime(enq)
                        .build();
                boolean ok = taskQueue.enqueue(entry);
                if (ok) recovered++;
                else skipped++;
            }
            log.info("[QueueRecovery] 回灌完成：成功 {} 条，跳过 {} 条，当前内存队列长度 {}",
                    recovered, skipped, taskQueue.size());
        } catch (Exception e) {
            log.error("[QueueRecovery] 队列回灌失败：{}", e.getMessage(), e);
        }
    }
}
