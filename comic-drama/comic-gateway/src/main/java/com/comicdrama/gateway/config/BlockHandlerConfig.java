package com.comicdrama.gateway.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sentinel 网关降级处理器配置。
 * <p>
 * 自定义 Sentinel 降级响应，返回统一 JSON 格式。
 * 注：网关适配器依赖需在运行时提供（sentinel-adapter-gateway-webflux），
 * 编译期仅保留核心 Sentinel 注解支持。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class BlockHandlerConfig {

    private final ObjectMapper objectMapper;

    /**
     * 统一降级处理器（兼容 Sentinel Gateway 未来接入）。
     * 当 sentinel-adapter-gateway-webflux 可用时，可通过 GatewayCallbackManager 注册。
     */
    @Bean
    public UnifiedBlockHandler unifiedBlockHandler() {
        return new UnifiedBlockHandler(objectMapper);
    }

    /**
     * 网关全局过滤器：为所有请求添加 Sentinel 保护。
     */
    @Bean
    public SentinelGatewayGlobalFilter sentinelGatewayGlobalFilter() {
        return new SentinelGatewayGlobalFilter();
    }

    /**
     * 统一降级处理器。
     */
    public static class UnifiedBlockHandler {

        private final ObjectMapper objectMapper;

        public UnifiedBlockHandler(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        public Mono<Void> handle(ServerWebExchange exchange, Throwable cause) {
            ServerHttpResponse response = exchange.getResponse();
            response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

            String json = buildBlockResponse(cause);
            DataBuffer buffer = response.bufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8));

            return response.writeWith(Mono.just(buffer));
        }

        private String buildBlockResponse(Throwable cause) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("code", 1001);
            body.put("msg", cause != null ? "请求过于频繁，请稍后再试" : "服务暂不可用");
            body.put("data", null);
            body.put("timestamp", System.currentTimeMillis());

            try {
                return objectMapper.writeValueAsString(body);
            } catch (JsonProcessingException e) {
                return "{\"code\":1001,\"msg\":\"服务暂不可用\",\"data\":null,\"timestamp\":" + System.currentTimeMillis() + "}";
            }
        }
    }

    /**
     * Sentinel 网关全局过滤器占位实现。
     * 真正的 SentinelGatewayFilter 由 sentinel-adapter-gateway-webflux 提供。
     */
    public static class SentinelGatewayGlobalFilter implements org.springframework.cloud.gateway.filter.GlobalFilter, Ordered {

        @Override
        public Mono<Void> filter(ServerWebExchange exchange, org.springframework.cloud.gateway.filter.GatewayFilterChain chain) {
            ServerHttpRequest request = exchange.getRequest();
            // 预留：接入 sentinel-adapter-gateway-webflux 后启用真实过滤
            return chain.filter(exchange);
        }

        @Override
        public int getOrder() {
            return -100;
        }
    }
}
