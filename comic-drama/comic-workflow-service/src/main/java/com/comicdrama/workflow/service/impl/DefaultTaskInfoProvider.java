package com.comicdrama.workflow.service.impl;

import com.comicdrama.common.dto.TaskCreateDTO;
import com.comicdrama.common.service.TaskInfoProvider;
import com.comicdrama.common.service.WorkflowTaskInfo;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 默认任务信息提供者实现。
 * 使用 Caffeine 缓存，设置最大容量和过期时间，防止内存无限增长导致 OOM。
 */
@Component
public class DefaultTaskInfoProvider implements TaskInfoProvider {

    /** 最大缓存任务数：超过后按 LRU 淘汰 */
    private static final int MAX_CACHE_SIZE = 5000;

    /** 缓存过期时间：任务信息在 2 小时无访问后自动淘汰 */
    private static final long CACHE_EXPIRE_HOURS = 2;

    private final Cache<Long, WorkflowTaskInfo> taskCache = Caffeine.newBuilder()
            .maximumSize(MAX_CACHE_SIZE)
            .expireAfterAccess(CACHE_EXPIRE_HOURS, TimeUnit.HOURS)
            .build();

    private final AtomicLong taskNoSeq = new AtomicLong(2026000000L);

    public void registerTask(Long taskId, Long userId, String title, TaskCreateDTO dto) {
        WorkflowTaskInfo existing = taskCache.getIfPresent(taskId);
        if (existing != null && dto != null) {
            // 合并策略：以已有 DTO 为基准，仅用非空字段覆盖，避免空 DTO 覆盖原始参数
            TaskCreateDTO merged = existing.requestDTO();
            if (merged == null) {
                merged = new TaskCreateDTO();
            }
            if (dto.getTitle() != null) merged.setTitle(dto.getTitle());
            if (dto.getStoryRequirement() != null) merged.setStoryRequirement(dto.getStoryRequirement());
            if (dto.getDuration() != null) merged.setDuration(dto.getDuration());
            if (dto.getAspectRatio() != null) merged.setAspectRatio(dto.getAspectRatio());
            if (dto.getResolution() != null) merged.setResolution(dto.getResolution());
            if (dto.getVoiceEnabled() != null) merged.setVoiceEnabled(dto.getVoiceEnabled());
            if (dto.getExecMode() != null) merged.setExecMode(dto.getExecMode());
            if (dto.getArtStyle() != null) merged.setArtStyle(dto.getArtStyle());
            if (dto.getVisualStyle() != null) merged.setVisualStyle(dto.getVisualStyle());
            if (dto.getRemark() != null) merged.setRemark(dto.getRemark());
            taskCache.put(taskId, new WorkflowTaskInfo(taskId, existing.taskNo(), userId,
                    title != null ? title : existing.title(), merged));
        } else {
            String taskNo = "T" + taskNoSeq.incrementAndGet();
            taskCache.put(taskId, new WorkflowTaskInfo(taskId, taskNo, userId, title, dto));
        }
    }

    @Override
    public WorkflowTaskInfo getTaskInfo(Long taskId) {
        WorkflowTaskInfo info = taskCache.getIfPresent(taskId);
        if (info == null) {
            return new WorkflowTaskInfo(taskId, "T" + taskId, 1L, "Demo Task", new TaskCreateDTO());
        }
        return info;
    }
}