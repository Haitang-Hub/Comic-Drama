package com.comicdrama.workflow.service.impl;

import com.comicdrama.common.service.TaskPauseChecker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 任务暂停检查器实现。
 * 通过查询 comic_task 表的 status 字段判断任务是否已被暂停。
 * 为避免频繁数据库查询（批量步骤每条产物前都会调用），采用短暂本地缓存（200ms TTL）。
 */
@Slf4j
@Component
public class TaskPauseCheckerImpl implements TaskPauseChecker {

    private final JdbcTemplate jdbcTemplate;

    /** 暂停状态码（与 TaskStatus.PAUSED 对齐） */
    private static final int PAUSED_STATUS = 4;

    /** 缓存 TTL（毫秒）：200ms 内复用查询结果，避免批量步骤中每条产物都查 DB */
    private static final long CACHE_TTL_MS = 200;

    /** 本地缓存：taskId -> {paused, timestamp} */
    private final ConcurrentHashMap<Long, CacheEntry> pauseCache = new ConcurrentHashMap<>();

    private record CacheEntry(boolean paused, long timestamp) {}

    public TaskPauseCheckerImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean isPaused(Long taskId) {
        if (taskId == null) {
            return false;
        }

        // 1. 先查本地缓存
        long now = System.currentTimeMillis();
        CacheEntry cached = pauseCache.get(taskId);
        if (cached != null && (now - cached.timestamp()) < CACHE_TTL_MS) {
            return cached.paused();
        }

        // 2. 缓存未命中或已过期，查数据库
        try {
            Integer status = jdbcTemplate.queryForObject(
                    "SELECT status FROM comic_task WHERE id = ?",
                    Integer.class, taskId);
            boolean paused = status != null && status == PAUSED_STATUS;
            pauseCache.put(taskId, new CacheEntry(paused, now));
            return paused;
        } catch (Exception e) {
            log.warn("检查任务暂停状态失败 taskId={}: {}", taskId, e.getMessage());
            return false;
        }
    }
}
