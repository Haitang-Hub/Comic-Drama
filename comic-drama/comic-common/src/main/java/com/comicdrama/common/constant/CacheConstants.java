package com.comicdrama.common.constant;

/** 缓存与队列通道常量 */
public final class CacheConstants {

    private CacheConstants() {}

    /** WebSocket / MessageBroadcaster 通道 */
    public static final String CHANNEL_TASK_PROGRESS = "task_progress";
    public static final String CHANNEL_TASK_STATUS = "task_status";

    /** Caffeine 缓存 key 前缀 */
    public static final String CACHE_PROMPT_TEMPLATE = "prompt_template";
    public static final String CACHE_SYS_CONFIG = "sys_config";
    public static final String CACHE_AI_MODEL = "ai_model";
}
