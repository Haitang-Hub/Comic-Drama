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
 * 三写策略：
 * 1. 本地广播（同 JVM 内事件）
 * 2. REST 调用 task-service 持久化进度日志（供前端 HTTP 轮询读取）
 * 3. REST 调用 gateway 转发 WebSocket 事件（跨 JVM 推送至前端）
 * <p>
 * 文案精简规则（用于前端进度条上方的状态文案，避免冗长）：
 * - 未传 message 时按 itemDone/itemTotal 合成，如 "3/8 已完成"、"开始生成"
 * - message 过长（>20字）时只保留到最近一个"。"/"；"之前
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

    /** 中文步骤名（与前端 STEP_NAMES 对齐，避免广播里带过长的英文 nodeKey/CODE） */
    private static final Map<Integer, String> STEP_NAMES = Map.of(
            1, "故事摘要",
            2, "分镜脚本",
            3, "资产设计",
            4, "资产绘图",
            5, "衍生绘图",
            6, "分镜绘图",
            7, "配音合成",
            8, "视频生成",
            9, "视频合并"
    );

    @Override
    public void record(Long taskId, Integer step, String stepName,
                       Integer progress, Integer totalProgress, String message,
                       Integer itemDone, Integer itemTotal) {
        String actualStepName = stepName != null ? stepName :
                (step != null ? STEP_NAMES.getOrDefault(step, "步骤" + step) : null);
        String actualMessage = compactMessage(message, itemDone, itemTotal);

        log.debug("进度记录 taskId={}, step={}({}), progress={}/{}, done={}/total={}, msg={}",
                taskId, step, actualStepName, progress, totalProgress, itemDone, itemTotal, actualMessage);

        Integer status = 1; // RUNNING（默认1：生成中）
        if (totalProgress != null && totalProgress >= 100 && progress != null && progress >= 100) {
            status = 2; // SUCCESS 标记
        }

        // 1. 本地广播（同 JVM 内事件）
        TaskProgressEvent event = new TaskProgressEvent(this, taskId, step, actualStepName,
                "step_" + step, progress, totalProgress, itemDone, itemTotal, status, actualMessage);
        broadcaster.publish(CacheConstants.CHANNEL_TASK_PROGRESS, event);

        // 2. 持久化到 task-service（供 HTTP 轮询读取）—— progress 字段存 totalProgress 兼容老前端
        try {
            Map<String, Object> logPayload = new HashMap<>();
            logPayload.put("taskId", taskId);
            logPayload.put("step", step);
            logPayload.put("stepName", actualStepName);
            logPayload.put("nodeType", "step_" + step);
            logPayload.put("progress", totalProgress);
            logPayload.put("stepProgress", progress);
            logPayload.put("itemDone", itemDone);
            logPayload.put("itemTotal", itemTotal);
            logPayload.put("message", actualMessage);
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
            wsPayload.put("stepName", actualStepName);
            wsPayload.put("progress", progress);
            wsPayload.put("totalProgress", totalProgress);
            wsPayload.put("itemDone", itemDone);
            wsPayload.put("itemTotal", itemTotal);
            wsPayload.put("status", status);
            wsPayload.put("message", actualMessage);
            wsPayload.put("timestamp", System.currentTimeMillis());

            restTemplate.postForObject(gatewayUrl + "/api/internal/progress", wsPayload, Void.class);
        } catch (Exception e) {
            log.debug("Gateway 进度转发失败（降级为 HTTP 轮询）: {}", e.getMessage());
        }
    }

    /**
     * 文案压缩：
     * - message 为空 → 从 itemDone/itemTotal 合成一句话（"开始生成" / "2/8 已完成" / "已完成"）
     * - message 过长 → 截断到第一个"。"或";"之前，最多20字
     */
    private static String compactMessage(String msg, Integer done, Integer total) {
        if (msg == null || msg.isBlank()) {
            if (done == null || total == null) return "开始生成";
            if (done >= total) return "已完成";
            if (done <= 0) return total + " 项，开始生成";
            return done + "/" + total + " 已完成";
        }
        int dot = msg.indexOf('。');
        int semi = msg.indexOf('；');
        int semiEn = msg.indexOf(';');
        int cut = Integer.MAX_VALUE;
        if (dot > 0) cut = Math.min(cut, dot);
        if (semi > 0) cut = Math.min(cut, semi);
        if (semiEn > 0) cut = Math.min(cut, semiEn);
        if (cut < msg.length()) msg = msg.substring(0, cut);
        if (msg.length() > 20) msg = msg.substring(0, 20);
        return msg;
    }
}
