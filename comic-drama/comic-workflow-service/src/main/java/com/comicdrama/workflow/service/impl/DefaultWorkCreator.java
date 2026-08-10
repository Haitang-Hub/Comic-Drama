package com.comicdrama.workflow.service.impl;

import com.comicdrama.common.service.WorkCreator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 默认作品创建器实现（内存版，Phase-2 替换为 Feign 调用 resource-service）。
 */
@Slf4j
@Component
public class DefaultWorkCreator implements WorkCreator {

    private final AtomicLong workSeq = new AtomicLong(1000L);

    @Override
    public Long createComicWork(Long taskId, Long userId, String title) {
        Long workId = workSeq.incrementAndGet();
        log.info("createComicWork workId={}, taskId={}, userId={}, title={}", workId, taskId, userId, title);
        return workId;
    }
}