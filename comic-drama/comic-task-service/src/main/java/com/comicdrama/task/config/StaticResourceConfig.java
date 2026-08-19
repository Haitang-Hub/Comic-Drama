package com.comicdrama.task.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * 头像静态资源映射：将 /avatars/** 映射到本地头像目录，
 * 使 AuthServiceImpl.uploadAvatar 返回的 /avatars/{fileName} 可直接访问。
 */
@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    @Value("${upload.avatar-path:/data/avatars}")
    private String avatarPath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String basePath = Paths.get(avatarPath).toAbsolutePath().normalize().toString();
        registry.addResourceHandler("/avatars/**")
                .addResourceLocations("file:" + basePath + "/");
    }
}
