package com.comicdrama.workflow.service.impl;

import com.comicdrama.common.service.FailureReporter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 默认失败报告器实现（Phase-4：同时持久化到数据库）。
 */
@Slf4j
@Component
public class DefaultFailureReporter implements FailureReporter {

    private final JdbcTemplate jdbcTemplate;

    public DefaultFailureReporter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void reportFailure(Long taskId, Integer step, String stepCode,
                              String errorMsg, Throwable throwable) {
        log.error("reportFailure taskId={}, step={}, code={}, error={}", taskId, step, stepCode, errorMsg, throwable);
        try {
            String errorStack = null;
            if (throwable != null) {
                StringBuilder sb = new StringBuilder();
                sb.append(throwable.getClass().getName()).append(": ").append(throwable.getMessage()).append("\n");
                for (StackTraceElement element : throwable.getStackTrace()) {
                    sb.append("\tat ").append(element.toString()).append("\n");
                }
                errorStack = sb.toString();
            }
            jdbcTemplate.update(
                    "INSERT INTO task_failure_log (task_id, step, node_type, node_key, " +
                            "error_type, error_message, error_stack, retry_count, resolved) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, 0, 0)",
                    taskId, step, stepCode, stepCode + "_fail",
                    throwable != null ? throwable.getClass().getSimpleName() : "Unknown",
                    errorMsg, errorStack);
        } catch (Exception e) {
            log.error("reportFailure DB写入失败 taskId={}", taskId, e);
        }
    }
}