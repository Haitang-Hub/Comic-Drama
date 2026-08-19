package com.comicdrama.resource;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 资源服务：文件上传/签名URL + 作品管理。
 * Phase-1 本地磁盘存储；Phase-2 切换 MinIO（storage.type=minio）。
 */
@SpringBootApplication(scanBasePackages = "com.comicdrama")
@MapperScan("com.comicdrama.resource.mapper")
@EnableScheduling
public class ComicResourceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ComicResourceApplication.class, args);
    }
}
