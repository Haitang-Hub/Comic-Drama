package com.comicdrama.common.storage;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * 本地文件存储实现（Phase-1 默认）。
 * objectKey 直接作为存储路径，形如 {taskId}/images/{filename}。
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "storage", name = "type", havingValue = "local", matchIfMissing = true)
public class LocalFileStorageService implements StorageService {

    private final StorageProperties properties;

    public LocalFileStorageService(StorageProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() throws IOException {
        Path base = Paths.get(properties.getLocalBasePath()).toAbsolutePath().normalize();
        Files.createDirectories(base);
        log.info("LocalFileStorageService 初始化，basePath={}, type=local（Phase-2 可切换 minio）", base);
    }

    @Override
    public String upload(InputStream is, String objectKey, long size, String contentType) throws Exception {
        String fullKey = normalizeKey(objectKey);
        Path target = resolve(fullKey);
        Files.createDirectories(target.getParent());
        Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
        return fullKey;
    }

    @Override
    public InputStream download(String objectKey) throws Exception {
        Path target = resolve(objectKey);
        if (!Files.exists(target)) {
            throw new java.io.FileNotFoundException("文件不存在: " + objectKey);
        }
        return new FileInputStream(target.toFile());
    }

    @Override
    public void delete(String objectKey) throws Exception {
        Path target = resolve(objectKey);
        Files.deleteIfExists(target);
    }

    @Override
    public String signUrl(String objectKey, int expireSeconds) {
        // local 模式直接拼接可访问 URL（开发期）；生产应走签名 token
        String base = properties.getLocalBaseUrl();
        return base + "/" + objectKey.replace("\\", "/");
    }

    @Override
    public boolean exists(String objectKey) {
        return Files.exists(resolve(objectKey));
    }

    private Path resolve(String objectKey) {
        return Paths.get(properties.getLocalBasePath()).toAbsolutePath().normalize().resolve(objectKey.replace("/", File.separator));
    }

    private String normalizeKey(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("objectKey 不能为空");
        }
        return objectKey;
    }
}
