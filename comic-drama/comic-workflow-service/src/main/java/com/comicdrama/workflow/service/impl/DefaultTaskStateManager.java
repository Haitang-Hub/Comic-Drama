package com.comicdrama.workflow.service.impl;

import com.comicdrama.common.service.TaskStateManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默认任务状态管理器实现（Phase-4：同时持久化到数据库 + 内存缓存）。
 * 计划暂停（PlannedPause）：使用内存缓存 + DB(task_node_state step=100 控制行) 双重持久化。
 */
@Slf4j
@Component
public class DefaultTaskStateManager implements TaskStateManager {

    /** 计划暂停控制行在 task_node_state 中的占位 step（100 预留为系统控制位，非业务步骤） */
    private static final int PLAN_PAUSE_CONTROL_STEP = 100;

    private final JdbcTemplate jdbcTemplate;

    private final Map<Long, TaskState> stateCache = new ConcurrentHashMap<>();

    /** 内存缓存计划暂停到期时间（ms，epoch）；null=未设置，-1=永不超时，<=System.currentTimeMillis()=已过期 */
    private final Map<Long, Long> plannedPauseExpireCache = new ConcurrentHashMap<>();

    private record TaskState(int status, int currentStep, int progress,
                             String coverUrl, String finalVideoUrl,
                             String failureReason, String failureDetail) {}

    public DefaultTaskStateManager(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 服务启动后：扫描 DB 中 step=100（PLAN_PAUSE_CONTROL_STEP）控制行，
     * 1) 过期的 → 清掉控制行；
     * 2) 未过期的 → 回填内存缓存（否则重启后 plannedPause 设置丢失，用户无法续跑）。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void rehydratePlannedPauseOnStartup() {
        try {
            long now = System.currentTimeMillis();
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT task_id, remark FROM task_node_state WHERE step=? AND node_status=1",
                    PLAN_PAUSE_CONTROL_STEP);
            int rehydrated = 0, cleaned = 0;
            for (Map<String, Object> row : rows) {
                Long taskId = ((Number) row.get("task_id")).longValue();
                String remark = (String) row.get("remark");
                long expireMs;
                try { expireMs = Long.parseLong(remark); } catch (Exception e) { expireMs = Long.MAX_VALUE; }
                if (expireMs <= now) {
                    clearPlannedPause(taskId);
                    cleaned++;
                } else {
                    plannedPauseExpireCache.put(taskId, expireMs);
                    rehydrated++;
                }
            }
            if (rehydrated > 0 || cleaned > 0) {
                log.info("[计划暂停-启动恢复] 从DB回填{}条，清理过期{}条", rehydrated, cleaned);
            }
        } catch (Exception e) {
            log.warn("[计划暂停-启动恢复] 扫描失败，跳过: {}", e.getMessage());
        }
    }

    @Override
    public void markAsRunning(Long taskId, int currentStep, LocalDateTime startTime) {
        int initialStep = Math.max(0, currentStep - 1);
        log.info("markAsRunning taskId={}, step={}, initialStep={}", taskId, currentStep, initialStep);
        stateCache.put(taskId, new TaskState(1, initialStep, 0, null, null, null, null));
        try {
            jdbcTemplate.update(
                    "UPDATE comic_task SET status=1, current_step=?, start_time=? WHERE id=?",
                    initialStep, startTime, taskId);
            jdbcTemplate.update(
                    "UPDATE task_queue SET queue_status=1, started_time=? WHERE task_id=?",
                    startTime, taskId);
        } catch (Exception e) {
            log.error("markAsRunning DB更新失败 taskId={}", taskId, e);
        }
    }

    @Override
    public void updateStepProgress(Long taskId, int currentStep, int progress, int totalProgress) {
        log.debug("updateStepProgress taskId={}, step={}, progress={}/{}", taskId, currentStep, progress, totalProgress);
        TaskState old = stateCache.get(taskId);
        if (old != null) {
            stateCache.put(taskId, new TaskState(1, currentStep, totalProgress,
                    old.coverUrl(), old.finalVideoUrl(), null, null));
        }
        try {
            jdbcTemplate.update(
                    "UPDATE comic_task SET current_step=?, progress=? WHERE id=?",
                    currentStep, totalProgress, taskId);
        } catch (Exception e) {
            log.error("updateStepProgress DB更新失败 taskId={}", taskId, e);
        }
    }

    @Override
    public void markAsDone(Long taskId, int progress, int totalConsumeTime,
                           String coverUrl, String finalVideoUrl, LocalDateTime endTime) {
        markAsDone(taskId, progress, totalConsumeTime, coverUrl, finalVideoUrl, endTime, 9);
    }

    @Override
    public void markAsDone(Long taskId, int progress, int totalConsumeTime,
                           String coverUrl, String finalVideoUrl, LocalDateTime endTime,
                           int completedStep) {
        markAsDone(taskId, progress, totalConsumeTime, coverUrl, finalVideoUrl, endTime, completedStep, null);
    }

    @Override
    public void markAsDone(Long taskId, int progress, int totalConsumeTime,
                           String coverUrl, String finalVideoUrl, LocalDateTime endTime,
                           int completedStep, String manifestJson) {
        log.info("markAsDone taskId={}, progress={}, consume={}ms, step={}, manifestLen={}", taskId, progress, totalConsumeTime, completedStep, manifestJson != null ? manifestJson.length() : 0);
        stateCache.put(taskId, new TaskState(2, completedStep, 100, coverUrl, finalVideoUrl, null, null));
        try {
            String sql = "UPDATE comic_task SET status=2, progress=100, current_step=?, " +
                    "end_time=?, total_consume_time=?, cover_url=?, final_video_url=?, final_work_manifest=? WHERE id=?";
            jdbcTemplate.update(sql, completedStep, endTime, totalConsumeTime, coverUrl, finalVideoUrl, manifestJson, taskId);
            jdbcTemplate.update(
                    "UPDATE task_queue SET queue_status=2, finished_time=? WHERE task_id=?",
                    endTime, taskId);
        } catch (Exception e) {
            log.error("markAsDone DB更新失败 taskId={}", taskId, e);
        }
    }

    @Override
    public void markAsFailed(Long taskId, int failureStep, String failureReason,
                             String failureDetail, LocalDateTime endTime) {
        log.error("markAsFailed taskId={}, step={}, reason={}", taskId, failureStep, failureReason);
        TaskState old = stateCache.get(taskId);
        // 计算失败时的进度：已完成步骤的进度 + 当前步骤的起始位置（按9步均等权重）
        // 前 (failureStep-1) 步已完成，每步占 100/9 ≈ 11.1%
        int calculatedProgress = ((failureStep - 1) * 100) / 9;
        stateCache.put(taskId, new TaskState(3, failureStep, calculatedProgress,
                old != null ? old.coverUrl() : null,
                old != null ? old.finalVideoUrl() : null,
                failureReason, failureDetail));
        try {
            jdbcTemplate.update(
                    "UPDATE comic_task SET status=3, current_step=?, progress=?, failure_step=?, failure_reason=?, " +
                            "failure_detail=?, end_time=? WHERE id=?",
                    failureStep, calculatedProgress, failureStep, failureReason, failureDetail, endTime, taskId);
            jdbcTemplate.update(
                    "UPDATE task_queue SET queue_status=3, finished_time=? WHERE task_id=?",
                    endTime, taskId);
        } catch (Exception e) {
            log.error("markAsFailed DB更新失败 taskId={}", taskId, e);
        }
    }

    @Override
    public void markAsPaused(Long taskId, int pausedStep, LocalDateTime pauseTime) {
        log.info("markAsPaused taskId={}, step={}", taskId, pausedStep);
        TaskState old = stateCache.get(taskId);
        int calculatedProgress = ((pausedStep - 1) * 100) / 9;
        stateCache.put(taskId, new TaskState(4, pausedStep, calculatedProgress,
                old != null ? old.coverUrl() : null,
                old != null ? old.finalVideoUrl() : null,
                null, null));
        try {
            jdbcTemplate.update(
                    "UPDATE comic_task SET status=4, current_step=?, progress=?, end_time=? WHERE id=?",
                    pausedStep, calculatedProgress, pauseTime, taskId);
            jdbcTemplate.update(
                    "UPDATE task_queue SET queue_status=3, finished_time=? WHERE task_id=?",
                    pauseTime, taskId);
        } catch (Exception e) {
            log.error("markAsPaused DB更新失败 taskId={}", taskId, e);
        }
    }

    @Override
    public int getStatus(Long taskId) {
        if (taskId == null) return -1;
        TaskState cached = stateCache.get(taskId);
        if (cached != null) {
            return cached.status();
        }
        try {
            Integer dbStatus = jdbcTemplate.queryForObject(
                    "SELECT status FROM comic_task WHERE id=?",
                    Integer.class, taskId);
            return dbStatus != null ? dbStatus : -1;
        } catch (Exception e) {
            log.warn("getStatus DB查询失败 taskId={}", taskId, e);
            return -1;
        }
    }

    // ======== 计划暂停（完成此阶段）语义：等当前步跑完后再停 ========

    @Override
    public void setPlannedPause(Long taskId, boolean plannedPauseFlag, int expireMinutes) {
        if (taskId == null) return;
        try {
            if (plannedPauseFlag) {
                long expireMs = expireMinutes > 0
                        ? System.currentTimeMillis() + (long) expireMinutes * 60_000L
                        : Long.MAX_VALUE;
                plannedPauseExpireCache.put(taskId, expireMs);
                // 写入 DB：node_status=1 标记启用；remark 存到期毫秒时间戳字符串
                String remark = String.valueOf(expireMs);
                jdbcTemplate.update(
                        "INSERT INTO task_node_state(task_id, step, node_status, remark) VALUES(?,?,?,?) " +
                                "ON DUPLICATE KEY UPDATE node_status=VALUES(node_status), remark=VALUES(remark), start_time=NULL, end_time=NULL",
                        taskId, PLAN_PAUSE_CONTROL_STEP, 1, remark);
                log.info("[计划暂停] 设置成功 taskId={}, expireMinutes={}", taskId, expireMinutes);
            } else {
                clearPlannedPause(taskId);
            }
        } catch (Exception e) {
            log.error("setPlannedPause 失败 taskId={}, flag={}", taskId, plannedPauseFlag, e);
        }
    }

    @Override
    public boolean isPlannedPause(Long taskId) {
        if (taskId == null) return false;
        long now = System.currentTimeMillis();

        // 先查内存缓存
        Long cached = plannedPauseExpireCache.get(taskId);
        if (cached != null) {
            if (cached > now) return true;
            // 已过期：清理后返回 false
            clearPlannedPause(taskId);
            return false;
        }

        // 内存没命中 → 查 DB 控制行
        try {
            java.util.List<String> remarkList = jdbcTemplate.query(
                    "SELECT remark FROM task_node_state WHERE task_id=? AND step=? AND node_status=1 LIMIT 1",
                    (rs, rn) -> rs.getString("remark"),
                    taskId, PLAN_PAUSE_CONTROL_STEP);
            if (remarkList == null || remarkList.isEmpty()) return false;
            String remark = remarkList.get(0);
            long expireMs;
            try { expireMs = Long.parseLong(remark); } catch (Exception e) { expireMs = Long.MAX_VALUE; }
            if (expireMs <= now) {
                clearPlannedPause(taskId);
                return false;
            }
            // 回填内存缓存
            plannedPauseExpireCache.put(taskId, expireMs);
            return true;
        } catch (Exception e) {
            log.warn("isPlannedPause DB查询失败 taskId={}", taskId, e);
            return false;
        }
    }

    @Override
    public void clearPlannedPause(Long taskId) {
        if (taskId == null) return;
        plannedPauseExpireCache.remove(taskId);
        try {
            jdbcTemplate.update("UPDATE task_node_state SET node_status=0, remark=NULL " +
                    "WHERE task_id=? AND step=?", taskId, PLAN_PAUSE_CONTROL_STEP);
            log.debug("[计划暂停] 清除标记 taskId={}", taskId);
        } catch (Exception e) {
            log.warn("clearPlannedPause DB更新失败 taskId={}", taskId, e);
        }
    }

    /**
     * 将 overrides 字段增量 UPDATE 到 comic_task，避免服务重启后丢失。
     */
    @Override
    public void persistOverrides(Long taskId, String title, String storyReq,
                                 String aspectRatio, String resolution,
                                 Integer voiceEnabled, Integer execMode,
                                 String artStyle, String visualStyle, String remark) {
        if (taskId == null) return;
        StringBuilder sql = new StringBuilder(256);
        sql.append("UPDATE comic_task SET ");
        java.util.List<Object> args = new java.util.ArrayList<>(10);
        boolean first = true;
        first = appendIf(sql, args, first, "title", title);
        first = appendIf(sql, args, first, "story_requirement", storyReq);
        first = appendIf(sql, args, first, "aspect_ratio", aspectRatio);
        first = appendIf(sql, args, first, "resolution", resolution);
        if (voiceEnabled != null) {
            if (!first) sql.append(", ");
            sql.append("voice_enabled=?");
            args.add(voiceEnabled);
            first = false;
        }
        if (execMode != null) {
            if (!first) sql.append(", ");
            sql.append("exec_mode=?");
            args.add(execMode);
            first = false;
        }
        first = appendIf(sql, args, first, "art_style", artStyle);
        first = appendIf(sql, args, first, "visual_style", visualStyle);
        first = appendIf(sql, args, first, "remark", remark);
        if (first) {
            return; // 没有任何非空字段
        }
        sql.append(" WHERE id=?");
        args.add(taskId);
        try {
            jdbcTemplate.update(sql.toString(), args.toArray());
            log.info("[persistOverrides] comic_task 已更新 taskId={}", taskId);
        } catch (Exception e) {
            log.error("[persistOverrides] 更新comic_task失败 taskId={}, sql={}", taskId, sql, e);
        }
    }

    private boolean appendIf(StringBuilder sql, java.util.List<Object> args,
                             boolean first, String column, String value) {
        if (value == null) return first;
        if (!first) sql.append(", ");
        sql.append(column).append("=?");
        args.add(value);
        return false;
    }
}