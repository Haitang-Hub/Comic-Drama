package com.comicdrama.workflow.service.impl;

import com.comicdrama.common.broadcast.MessageBroadcaster;
import com.comicdrama.common.broadcast.event.TaskProgressEvent;
import com.comicdrama.common.constant.CacheConstants;
import com.comicdrama.common.service.ProgressReporter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 默认进度报告器实现（Phase-4：同时持久化到数据库 + 广播事件）。
 */
@Slf4j
@Component
public class DefaultProgressReporter implements ProgressReporter {

    private final MessageBroadcaster broadcaster;
    private final JdbcTemplate jdbcTemplate;

    public DefaultProgressReporter(MessageBroadcaster broadcaster, JdbcTemplate jdbcTemplate) {
        this.broadcaster = broadcaster;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void reportProgress(Long taskId, Integer step, Integer progress,
                              Integer totalProgress, String message) {
        log.debug("reportProgress taskId={}, step={}, progress={}/{}", taskId, step, progress, totalProgress);
        broadcaster.publish(CacheConstants.CHANNEL_TASK_PROGRESS,
                new TaskProgressEvent(this, taskId, step, "step_" + step, progress, totalProgress, message));
        try {
            jdbcTemplate.update(
                    "INSERT INTO task_progress_log (task_id, step, node_type, node_key, progress, total_progress, message, is_pushed) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, 0)",
                    taskId, step, "step_" + step, "step_" + step,
                    progress != null ? progress : 0,
                    totalProgress != null ? totalProgress : 0,
                    message);
        } catch (Exception e) {
            log.error("reportProgress DB写入失败 taskId={}", taskId, e);
        }
    }
}