package com.comicdrama.system.config;

import com.comicdrama.common.constant.CacheConstants;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Caffeine 热生效缓存配置。
 * 缓存 promptTemplate、systemConfig、aiModelConfig 三类热数据，
 * 写入后 10 分钟自动过期，支持运行时动态更新。
 */
@Configuration
public class CaffeineCacheConfig {

    @Bean
    public Cache<String, Object> promptTemplateCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .maximumSize(500)
                .build();
    }

    @Bean
    public Cache<String, Object> systemConfigCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .maximumSize(200)
                .build();
    }

    @Bean
    public Cache<String, Object> aiModelConfigCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .maximumSize(100)
                .build();
    }
}
