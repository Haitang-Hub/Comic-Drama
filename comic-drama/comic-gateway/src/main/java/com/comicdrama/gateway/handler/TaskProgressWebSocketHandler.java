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
import reactor.core.publisher.EmitterProcessor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 任务进度 WebSocket Handler。
 * <p>
 * - 处理 WebSocket 连接/断开
 * - 订阅 MessageBroadcaster 通道，将事件推送到客户端（用 EmitterProcessor 桥接成 Flux，
 *   确保 send 流被 session.send 真正订阅，不会"发送了但前端收不到"）
 * - 心跳检测（30s ping 服务端发，pong 客户端回）
 * - 连接时校验 token（从 query 参数或 header 获取）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskProgressWebSocketHandler implements WebSocketHandler {

    private final MessageBroadcaster messageBroadcaster;
    private final ObjectMapper objectMapper;

    /** taskId → WebSocketSession 映射（用于调试，主发送链路走 EmitterProcessor） */
    @SuppressWarnings("unused")
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

        // 用 EmitterProcessor 把 push 风格订阅转为 Flux（send 必需冷 publisher 才能真正发出去）
        EmitterProcessor<String> outputSink = EmitterProcessor.create();
        AtomicBoolean subscribed = new AtomicBoolean(false);
        java.util.function.Consumer<Object> subscriber = event -> {
            try {
                Long eventTaskId = null;
                if (event instanceof Map) {
                    Object tid = ((Map<?, ?>) event).get("taskId");
                    if (tid == null) return;
                    eventTaskId = tid instanceof Long ? (Long) tid : ((Number) tid).longValue();
                } else if (event instanceof com.comicdrama.common.broadcast.event.TaskProgressEvent) {
                    com.comicdrama.common.broadcast.event.TaskProgressEvent evt =
                            (com.comicdrama.common.broadcast.event.TaskProgressEvent) event;
                    eventTaskId = evt.getTaskId();
                }
                if (eventTaskId == null || !eventTaskId.equals(taskId)) return;

                Map<String, Object> wrapper = Map.of(
                        "type", "progress",
                        "data", event,
                        "timestamp", System.currentTimeMillis()
                );
                String json = toJson(wrapper);
                if (subscribed.get()) {
                    outputSink.onNext(json);
                }
                // 否则：订阅尚未建立，丢弃（本就是"实时推送"，丢失一帧可由前端轮询兜底）
            } catch (Exception e) {
                log.error("处理进度广播事件失败 taskId={}", taskId, e);
            }
        };
        messageBroadcaster.subscribe(CacheConstants.CHANNEL_TASK_PROGRESS, subscriber);

        // 心跳：每 30s 从服务端下发一个 ping frame
        Flux<WebSocketMessage> heartbeatFlux = Flux.interval(Duration.ofSeconds(30))
                .map(tick -> session.textMessage(toJson(Map.of("type", "ping"))));

        // 进度推送流：EmitterProcessor -> WebSocketMessage
        Flux<WebSocketMessage> progressFlux = outputSink
                .map(session::textMessage)
                .onBackpressureDrop(dropped ->
                        log.debug("progress backpressure drop, taskId={}", taskId));

        // 合并：心跳 + 进度推送
        Flux<WebSocketMessage> sendFlux = Flux.merge(progressFlux, heartbeatFlux)
                .doOnSubscribe(s -> subscribed.set(true))
                .doFinally(sig -> subscribed.set(false));

        // 接收：只做日志 + pong 应答，receive 永不结束（WebSocket 连接断开才 end）
        Mono<Void> receive = session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .doOnNext(text -> handleClientMessage(session, text, outputSink))
                .doOnError(e -> log.error("WebSocket receive error for taskId={}", taskId, e))
                .then();

        return session.send(sendFlux)
                .and(receive)
                .doOnSuccess(v -> {
                    sessionMap.remove(taskId);
                    log.info("WebSocket disconnected: taskId={}", taskId);
                })
                .doOnError(e -> {
                    sessionMap.remove(taskId);
                    log.error("WebSocket error for taskId={}", taskId, e);
                });
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

    private void handleClientMessage(org.springframework.web.reactive.socket.WebSocketSession session,
                                     String text,
                                     EmitterProcessor<String> sink) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> msg = objectMapper.readValue(text, Map.class);
            String type = (String) msg.get("type");
            if ("ping".equals(type)) {
                // 客户端发 ping → 回 pong（同连接级心跳）
                sink.onNext(toJson(Map.of("type", "pong")));
            } else if ("pong".equals(type)) {
                log.trace("Received pong from client, sessionId={}", session.getId());
            } else {
                log.debug("Unknown client message type={}, sessionId={}", type, session.getId());
            }
        } catch (JsonProcessingException e) {
            log.warn("Invalid JSON from client: {}", text);
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
