package com.comicdrama.workflow.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;

/**
 * AI 模型调用 RestTemplate 配置。
 * 为各 Invoker 提供统一的 HTTP 客户端，支持连接/读取超时。
 *
 * 注意：不使用 RestTemplateBuilder，因为其 requestFactory(Supplier) + setReadTimeout
 * 组合在 Spring Boot 3.2+ 中可能无法将超时正确应用到 SimpleClientHttpRequestFactory，
 * 导致 AI 调用卡死且不超时。直接创建 RestTemplate 确保超时生效。
 */
@Slf4j
@Configuration
public class AiRestTemplateConfig {

    @Bean
    public RestTemplate aiRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        // 关键：图生图/文生图场景需要上传大请求体（Base64嵌入）+ 等待模型服务端接收处理，
        // 原 300 秒在高并发下仍可能先于轮询超时（10分钟）触发，
        // 调大为 900 秒（15分钟），让业务层的轮询超时逻辑（ModelScopeInvoker.maxPollCount）先接管。
        factory.setReadTimeout(900000);
        RestTemplate restTemplate = new RestTemplate(factory);

        // 调试：打印已注册的 message converters
        log.info("AI RestTemplate 初始化: connectTimeout=10000ms, readTimeout=900000ms (15min, 适配长轮询/大请求体)");
        restTemplate.getMessageConverters().forEach(c ->
            log.info("  message converter: {}", c.getClass().getSimpleName())
        );

        // 确保有 Jackson converter 能序列化 Map body 为 JSON
        boolean hasJackson = restTemplate.getMessageConverters().stream()
                .anyMatch(c -> c instanceof MappingJackson2HttpMessageConverter);
        if (!hasJackson) {
            log.warn("未找到 MappingJackson2HttpMessageConverter，手动添加");
            restTemplate.getMessageConverters().add(new MappingJackson2HttpMessageConverter());
        }

        return restTemplate;
    }
}