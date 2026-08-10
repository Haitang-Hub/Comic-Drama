package com.comicdrama.gateway.config;

import com.comicdrama.gateway.handler.TaskProgressWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;

import java.util.Map;

/**
 * WebSocket 配置（Spring Cloud Gateway Reactor 原生 WebSocket）。
 * 注册 WebSocket Handler，路径：/ws/task/{taskId}
 */
@Configuration
@RequiredArgsConstructor
public class WebSocketConfig {

    private final TaskProgressWebSocketHandler taskProgressHandler;

    @Bean
    public SimpleUrlHandlerMapping webSocketHandlerMapping() {
        Map<String, WebSocketHandler> urlMap = Map.of(
                "/ws/task/{taskId}", taskProgressHandler
        );
        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setUrlMap(urlMap);
        mapping.setOrder(-1);
        return mapping;
    }

    @Bean
    public WebSocketHandlerAdapter webSocketHandlerAdapter() {
        return new WebSocketHandlerAdapter();
    }
}
