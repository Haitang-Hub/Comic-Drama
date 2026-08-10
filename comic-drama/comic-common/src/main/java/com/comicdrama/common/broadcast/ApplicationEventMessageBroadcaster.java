package com.comicdrama.common.broadcast;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 基于 Spring ApplicationEvent 的轻量消息广播实现（单节点）。
 * Phase-5 可替换为 Redis Pub/Sub 实现，业务代码不感知。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApplicationEventMessageBroadcaster implements MessageBroadcaster {

    private final ApplicationEventPublisher eventPublisher;

    private final Map<String, List<Consumer<?>>> subscribers = new ConcurrentHashMap<>();

    @Override
    @SuppressWarnings("unchecked")
    public <T> void publish(String channel, T payload) {
        log.debug("publish channel={}, payload={}", channel, payload);
        List<Consumer<?>> consumers = subscribers.get(channel);
        if (consumers != null) {
            for (Consumer consumer : consumers) {
                try {
                    consumer.accept(payload);
                } catch (Exception e) {
                    log.error("Subscriber error for channel={}: {}", channel, e.getMessage(), e);
                }
            }
        }
        eventPublisher.publishEvent(new BroadcastEvent<>(this, channel, payload));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> void subscribe(String channel, Consumer<T> consumer) {
        subscribers.computeIfAbsent(channel, k -> new CopyOnWriteArrayList<>())
                .add(consumer);
        log.debug("subscribe channel={}, consumer={}", channel, consumer.getClass().getSimpleName());
    }

    @EventListener
    public void onEvent(BroadcastEvent<?> event) {
        // Spring Event bridge - subscribers are handled directly in publish()
        // This ensures integration with Spring's event system
    }

    public static class BroadcastEvent<T> {
        private final Object source;
        private final String channel;
        private final T payload;

        public BroadcastEvent(Object source, String channel, T payload) {
            this.source = source;
            this.channel = channel;
            this.payload = payload;
        }

        public Object getSource() { return source; }
        public String getChannel() { return channel; }
        public T getPayload() { return payload; }
    }
}