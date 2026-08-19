package com.comicdrama.resource.config;

import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.comicdrama.resource.service.ResourceFileService;

/**
 * 资源同步定时任务：从各中间产物表（asset_image / storyboard_image / scene_video / comic_work 等）
 * 扫描已有的文件 URL，去重后写入 resource_file 表，解决「资源中心 Tab 无数据」问题。
 *
 * <p>策略：
 * <ol>
 *   <li>应用启动完成后立即同步一次；</li>
 *   <li>随后每 3 分钟再同步一次（幂等，基于 objectKey 去重，重复执行无副作用）。</li>
 * </ol>
 */
@Slf4j
@Component
public class ResourceSyncScheduler {

    private final ResourceFileService resourceFileService;

    public ResourceSyncScheduler(ResourceFileService resourceFileService) {
        this.resourceFileService = resourceFileService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("[ResourceSyncScheduler] 应用启动完成，触发首次同步...");
        doSync();
    }

    /**
     * 每 3 分钟运行一次。initialDelay=5s 避开启动阶段其他初始化竞争。
     */
    @Scheduled(fixedDelay = 180_000, initialDelay = 5_000)
    public void scheduledSync() {
        doSync();
    }

    private void doSync() {
        try {
            long start = System.currentTimeMillis();
            Map<String, Integer> summary = resourceFileService.syncFromArtifactTables();
            long elapsed = System.currentTimeMillis() - start;
            log.info("[ResourceSyncScheduler] 同步完成：{} ms | {}", elapsed, summary);
        } catch (Exception e) {
            log.error("[ResourceSyncScheduler] 同步异常", e);
        }
    }
}
