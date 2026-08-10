package com.comicdrama.system.service;

import com.comicdrama.system.entity.OperationLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 操作日志异步写入服务。
 * 单独抽取以确保 @Async 生效（Spring 代理限制：同类内部调用 @Async 无效）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperationLogAsyncService {

    private final OperationLogService operationLogService;

    @Async
    public void saveLog(OperationLog operationLog) {
        try {
            operationLogService.save(operationLog);
        } catch (Exception e) {
            log.error("Failed to save operation log: {}", e.getMessage(), e);
        }
    }
}
