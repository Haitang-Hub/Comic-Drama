package com.comicdrama.task.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * 队列配置（Phase-5）。
 * <p>
 * 根据 {@code task.queue.type} 切换内存队列 / RocketMQ：
 * <ul>
 *   <li>{@code memory}（默认）：使用 InMemoryTaskQueue</li>
 *   <li>{@code rocketmq}：使用 RocketMQTaskQueue</li>
 * </ul>
 */
@Configuration
public class QueueConfig {

    @Value("${task.queue.type:memory}")
    private String queueType;

    @Value("${task.queue.poll-interval-ms:1000}")
    private long pollIntervalMs;

    @Value("${rocketmq.namesrv-addr:127.0.0.1:9876}")
    private String rocketmqNamesrvAddr;

    public String getQueueType() {
        return queueType;
    }

    public long getPollIntervalMs() {
        return pollIntervalMs;
    }

    public String getRocketmqNamesrvAddr() {
        return rocketmqNamesrvAddr;
    }
}