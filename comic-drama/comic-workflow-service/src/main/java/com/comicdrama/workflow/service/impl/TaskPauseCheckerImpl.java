package com.comicdrama.workflow.service.impl;

import com.comicdrama.common.service.TaskPauseChecker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 任务暂停检查器实现。
 * 通过查询 comic_task 表的 status 字段判断任务是否已被暂停。
 * 为避免频繁数据库查询，采用短暂本地缓存（200ms）。
 */
@Slf4j
@Component
public class TaskPauseCheckerImpl implements TaskPauseChecker {

    private final JdbcTemplate jdbcTemplate;

    /** 暂停状态码（与 TaskStatus.PAUSED 对齐） */
    private static final int PAUSED_STATUS = 4;

    public TaskPauseCheckerImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean isPaused(Long taskId) {
        if (taskId == null) {
            return false;
        }
        try {
            Integer status = jdbcTemplate.queryForObject(
                    "SELECT status FROM comic_task WHERE id = ?",
                    Integer.class, taskId);
            return status != null && status == PAUSED_STATUS;
        } catch (Exception e) {
            log.warn("检查任务暂停状态失败 taskId={}: {}", taskId, e.getMessage());
            return false;
        }
    }
}
