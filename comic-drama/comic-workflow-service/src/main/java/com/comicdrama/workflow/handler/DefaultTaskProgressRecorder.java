package com.comicdrama.workflow.handler;

import com.comicdrama.common.broadcast.MessageBroadcaster;
import com.comicdrama.common.broadcast.event.TaskProgressEvent;
import com.comicdrama.common.constant.CacheConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * 默认任务进度记录器实现。
 * <p>
 * 双写策略：
 * 1. 本地广播（供同 JVM 内的步骤处理使用）
 * 2. REST 调用 task-service 持久化进度日志（供前端 HTTP 轮询读取）
 * 3. REST 调用 gateway 转发 WebSocket 事件
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultTaskProgressRecorder implements TaskProgressRecorder {

    private final MessageBroadcaster broadcaster;
    private final RestTemplate restTemplate;

    @Value("${app.task-service.url:http://127.0.0.1:8103}")
    private String taskServiceUrl;

    @Value("${app.gateway.url:http://127.0.0.1:8070}")
    private String gatewayUrl;

    @Override
    public void record(Long taskId, Integer step, Integer progress, Integer totalProgress, String message) {
        log.debug("进度记录 taskId={}, step={}, progress={}, totalProgress={}, msg={}", taskId, step, progress, totalProgress, message);

        // 1. 本地广播（同 JVM 内事件）
        TaskProgressEvent event = new TaskProgressEvent(this, taskId, step, "step_" + step, progress, totalProgress, message);
        broadcaster.publish(CacheConstants.CHANNEL_TASK_PROGRESS, event);

        // 2. 持久化到 task-service（供 HTTP 轮询读取）
        try {
            Map<String, Object> logPayload = new HashMap<>();
            logPayload.put("taskId", taskId);
            logPayload.put("step", step);
            logPayload.put("nodeType", "step_" + step);
            logPayload.put("progress", totalProgress);
            logPayload.put("message", message);
            logPayload.put("isPushed", 0);

            restTemplate.postForObject(taskServiceUrl + "/api/progress", logPayload, Void.class);
        } catch (Exception e) {
            log.warn("进度日志持久化失败: taskId={}, step={}, error={}", taskId, step, e.getMessage());
        }

        // 3. 转发到 gateway 以推送到 WebSocket 客户端
        try {
            Map<String, Object> wsPayload = new HashMap<>();
            wsPayload.put("taskId", taskId);
            wsPayload.put("step", step);
            wsPayload.put("stepName", "step_" + step);
            wsPayload.put("progress", progress);
            wsPayload.put("totalProgress", totalProgress);
            wsPayload.put("message", message);
            wsPayload.put("status", 1);
            wsPayload.put("timestamp", System.currentTimeMillis());

            restTemplate.postForObject(gatewayUrl + "/api/internal/progress", wsPayload, Void.class);
        } catch (Exception e) {
            log.debug("Gateway 进度转发失败（可降级为 HTTP 轮询）: {}", e.getMessage());
        }
    }
}
