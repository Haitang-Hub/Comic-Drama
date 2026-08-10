package com.comicdrama.common.broadcast;

import java.util.function.Consumer;

/**
 * 消息广播抽象（轻量中间件）。
 * Phase-1 实现 {@link ApplicationEventMessageBroadcaster}（Spring ApplicationEvent，单节点），
 * Phase-5 替换为 Redis Pub/Sub 实现集群广播，业务代码不感知。
 */
public interface MessageBroadcaster {

    /** 发布消息到指定通道 */
    <T> void publish(String channel, T payload);

    /** 订阅指定通道（单节点实现通过 ApplicationEvent 监听；集群实现通过 Redis subscribe） */
    <T> void subscribe(String channel, Consumer<T> consumer);
}
