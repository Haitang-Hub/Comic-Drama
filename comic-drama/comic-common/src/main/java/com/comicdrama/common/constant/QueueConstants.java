package com.comicdrama.common.constant;

/** 队列/调度常量 */
public final class QueueConstants {

    private QueueConstants() {}

    /** 内存队列容量上限 */
    public static final int QUEUE_CAPACITY = 1000;

    /** 默认优先级（数字越小优先级越高） */
    public static final int DEFAULT_PRIORITY = 100;

    /** 队列轮询间隔（毫秒） */
    public static final long POLL_INTERVAL_MS = 1000L;

    /** WebSocket 心跳间隔（秒，与 system_config.websocket_heartbeat_interval 对齐） */
    public static final int WS_HEARTBEAT_INTERVAL = 30;

    /** WebSocket 最大重连次数 */
    public static final int WS_RECONNECT_MAX = 5;
}
