package com.comicdrama.gateway.handler;

import com.comicdrama.common.broadcast.MessageBroadcaster;
import com.comicdrama.common.constant.CacheConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 网关内部进度转发端点。
 * <p>
 * 供工作流服务将进度事件跨 JVM 转发到网关，再由网关推送到 WebSocket 客户端。
 * 路径：/api/internal/progress
 */
@Slf4j
@RestController
@RequestMapping("/api/internal")
@RequiredArgsConstructor
public class InternalProgressController {

    private final MessageBroadcaster messageBroadcaster;

    @PostMapping("/progress")
    public Mono<Map<String, Object>> forwardProgress(@RequestBody Map<String, Object> payload) {
        try {
            messageBroadcaster.publish(CacheConstants.CHANNEL_TASK_PROGRESS, payload);
            log.debug("网关收到进度转发事件: taskId={}, step={}", payload.get("taskId"), payload.get("step"));
        } catch (Exception e) {
            log.error("网关转发进度事件失败", e);
        }
        return Mono.just(Map.of("code", 200, "msg", "ok"));
    }
}
