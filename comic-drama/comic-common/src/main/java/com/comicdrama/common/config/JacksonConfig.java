package com.comicdrama.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Jackson 配置：
 * - Java8 时间类型序列化
 * - Long -> String（避免 JS 精度丢失）
 * - 忽略未知属性
 */
@Configuration
public class JacksonConfig {

    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    private static final String DATE_PATTERN = "yyyy-MM-dd";

    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);
        DateTimeFormatter df = DateTimeFormatter.ofPattern(DATE_PATTERN);

        JavaTimeModule javaTimeModule = new JavaTimeModule();
        javaTimeModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(dtf));
        javaTimeModule.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(dtf));
        javaTimeModule.addSerializer(LocalDate.class, new LocalDateSerializer(df));
        javaTimeModule.addDeserializer(LocalDate.class, new LocalDateDeserializer(df));

        // Long -> String，避免前端 JS Number 精度丢失
        SimpleModule longModule = new SimpleModule();
        longModule.addSerializer(Long.class, ToStringSerializer.instance);
        longModule.addSerializer(Long.TYPE, ToStringSerializer.instance);

        ObjectMapper mapper = builder.createXmlMapper(false).build();
        mapper.registerModule(javaTimeModule);
        mapper.registerModule(longModule);
        mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }
}
