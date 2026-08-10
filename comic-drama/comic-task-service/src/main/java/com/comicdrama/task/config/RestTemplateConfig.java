package com.comicdrama.task.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * HTTP 客户端配置。
 */
@Configuration
public class RestTemplateConfig {

    @Value("${workflow.service.url:http://127.0.0.1:8104}")
    private String workflowServiceUrl;

    @Value("${workflow.service.connect-timeout:5000}")
    private int connectTimeout;

    @Value("${workflow.service.read-timeout:300000}")
    private int readTimeout;

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return new RestTemplate(factory);
    }

    public String getWorkflowServiceUrl() {
        return workflowServiceUrl;
    }
}