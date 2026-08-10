package com.comicdrama.gateway.handler;

import cn.dev33.satoken.stp.StpUtil;
import com.comicdrama.common.broadcast.MessageBroadcaster;
import com.comicdrama.common.constant.CacheConstants;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 任务进度 WebSocket Handler。
 * <p>
 * - 处理 WebSocket 连接/断开
 * - 订阅 MessageBroadcaster 通道，将事件推送到客户端
 * - 心跳检测（30s ping/pong）
 * - 连接时校验 token（从 query 参数或 header 获取）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskProgressWebSocketHandler implements WebSocketHandler {

    private final MessageBroadcaster messageBroadcaster;
    private final ObjectMapper objectMapper;

    /** taskId → WebSocketSession 映射 */
    private final Map<Long, org.springframework.web.reactive.socket.WebSocketSession> sessionMap = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> handle(org.springframework.web.reactive.socket.WebSocketSession session) {
        String path = session.getHandshakeInfo().getUri().getPath();
        Long taskId = extractTaskId(path);
        if (taskId == null) {
            log.warn("Invalid WebSocket path: {}", path);
            return session.close();
        }

        String token = extractToken(session);
        if (!validateToken(token)) {
            log.warn("WebSocket auth failed for taskId={}", taskId);
            return session.close();
        }

        sessionMap.put(taskId, session);
        log.info("WebSocket connected: taskId={}, sessionId={}", taskId, session.getId());

        messageBroadcaster.subscribe(CacheConstants.CHANNEL_TASK_PROGRESS, event -> {
            try {
                if (event instanceof Map) {
                    Object tid = ((Map<?, ?>) event).get("taskId");
                    if (tid == null) return;
                    Long eventTaskId = tid instanceof Long ? (Long) tid : ((Number) tid).longValue();
                    if (eventTaskId.equals(taskId)) {
                        sendMessage(session, event);
                    }
                } else if (event instanceof com.comicdrama.common.broadcast.event.TaskProgressEvent) {
                    com.comicdrama.common.broadcast.event.TaskProgressEvent evt =
                            (com.comicdrama.common.broadcast.event.TaskProgressEvent) event;
                    if (evt.getTaskId() != null && evt.getTaskId().equals(taskId)) {
                        sendMessage(session, event);
                    }
                }
            } catch (Exception e) {
                log.error("Failed to process broadcast event for taskId={}", taskId, e);
            }
        });

        Mono<Void> heartbeat = session.send(
                Flux.interval(Duration.ofSeconds(30))
                        .map(tick -> session.textMessage(toJson(Map.of("type", "ping"))))
        );

        Mono<Void> receive = session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .doOnNext(text -> handleClientMessage(session, text))
                .doOnError(e -> log.error("WebSocket receive error for taskId={}", taskId, e))
                .then();

        return Mono.first(heartbeat, receive)
                .doOnSuccess(v -> log.info("WebSocket disconnected: taskId={}", taskId))
                .doOnError(e -> log.error("WebSocket error for taskId={}", taskId, e))
                .doFinally(sig -> sessionMap.remove(taskId));
    }

    private Long extractTaskId(String path) {
        String prefix = "/ws/task/";
        int idx = path.indexOf(prefix);
        if (idx == -1) return null;
        String idStr = path.substring(idx + prefix.length());
        try {
            return Long.parseLong(idStr);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String extractToken(org.springframework.web.reactive.socket.WebSocketSession session) {
        String query = session.getHandshakeInfo().getUri().getQuery();
        if (query != null && !query.isEmpty()) {
            for (String param : query.split("&")) {
                String[] kv = param.split("=", 2);
                if (kv.length == 2 && "token".equals(kv[0])) {
                    return kv[1];
                }
            }
        }
        return null;
    }

    private boolean validateToken(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        try {
            StpUtil.getLoginIdByToken(token);
            return true;
        } catch (Exception e) {
            log.warn("Token validation failed: {}", e.getMessage());
            return false;
        }
    }

    private void handleClientMessage(org.springframework.web.reactive.socket.WebSocketSession session, String text) {
        try {
            Map<String, Object> msg = objectMapper.readValue(text, Map.class);
            String type = (String) msg.get("type");
            if ("pong".equals(type)) {
                log.debug("Received pong from client");
            }
        } catch (JsonProcessingException e) {
            log.warn("Invalid JSON from client: {}", text);
        }
    }

    private void sendMessage(org.springframework.web.reactive.socket.WebSocketSession session, Object payload) {
        if (session.isOpen()) {
            try {
                Map<String, Object> wrapper = Map.of(
                        "type", "progress",
                        "data", payload,
                        "timestamp", System.currentTimeMillis()
                );
                session.send(Mono.just(session.textMessage(toJson(wrapper))))
                        .subscribe(
                                v -> {},
                                e -> log.error("Failed to send WebSocket message", e)
                        );
            } catch (Exception e) {
                log.error("Failed to wrap WebSocket message", e);
            }
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize JSON", e);
            return "{}";
        }
    }
}
