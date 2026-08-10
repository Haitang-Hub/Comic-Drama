package com.comicdrama.workflow.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 默认任务失败记录器实现。
 */
@Slf4j
@Component
public class DefaultTaskFailureRecorder implements TaskFailureRecorder {

    @Override
    public void record(Long taskId, Integer step, String nodeKey, String errorMsg, Throwable throwable) {
        log.error("失败记录 taskId={}, step={}, nodeKey={}, error={}", taskId, step, nodeKey, errorMsg, throwable);
    }
}