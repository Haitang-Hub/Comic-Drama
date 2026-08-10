package com.comicdrama.resource.config;

import com.comicdrama.common.storage.StorageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * 本地存储静态资源映射：将 /static/** 映射到本地存储目录，
 * 使 LocalFileStorageService.signUrl 生成的 URL 可直接访问（开发期）。
 * Phase-2 切 MinIO 后本配置不再生效（资源由 MinIO 直接提供）。
 */
@Configuration
@RequiredArgsConstructor
public class StaticResourceConfig implements WebMvcConfigurer {

    private final StorageProperties storageProperties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String basePath = Paths.get(storageProperties.getLocalBasePath())
                .toAbsolutePath().normalize().toString();
        registry.addResourceHandler("/static/**")
                .addResourceLocations("file:" + basePath + "/");
    }
}
