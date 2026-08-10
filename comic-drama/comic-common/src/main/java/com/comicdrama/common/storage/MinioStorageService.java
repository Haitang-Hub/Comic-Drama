package com.comicdrama.common.storage;

import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.InputStream;

/**
 * MinIO 存储实现（Phase-2 启用，storage.type=minio）。
 * 与 LocalFileStorageService 互斥；minio SDK 通过 comic-common optional 依赖引入，
 * 仅 resource-service 显式声明 minio 依赖时本类才会被加载（@ConditionalOnClass 守卫）。
 */
@Slf4j
@Service
@ConditionalOnClass(MinioClient.class)
@ConditionalOnProperty(prefix = "storage", name = "type", havingValue = "minio")
public class MinioStorageService implements StorageService {

    private final StorageProperties properties;
    private MinioClient minioClient;

    public MinioStorageService(StorageProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        StorageProperties.Minio m = properties.getMinio();
        this.minioClient = MinioClient.builder()
                .endpoint(m.getEndpoint())
                .credentials(m.getAccessKey(), m.getSecretKey())
                .build();
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(m.getBucket()).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(m.getBucket()).build());
                log.info("MinIO bucket 自动创建: {}", m.getBucket());
            }
            log.info("MinioStorageService 初始化完成，endpoint={}, bucket={}", m.getEndpoint(), m.getBucket());
        } catch (Exception e) {
            log.error("MinIO 初始化失败，请检查连接配置: {}", e.getMessage(), e);
        }
    }

    @Override
    public String upload(InputStream is, String objectKey, long size, String contentType) throws Exception {
        long s = size < 0 ? -1 : size;
        long partSize = 10 * 1024 * 1024L;
        minioClient.putObject(PutObjectArgs.builder()
                .bucket(properties.getMinio().getBucket())
                .object(objectKey)
                .stream(is, s, partSize)
                .contentType(contentType == null ? "application/octet-stream" : contentType)
                .build());
        return objectKey;
    }

    @Override
    public InputStream download(String objectKey) throws Exception {
        return minioClient.getObject(io.minio.GetObjectArgs.builder()
                .bucket(properties.getMinio().getBucket())
                .object(objectKey)
                .build());
    }

    @Override
    public void delete(String objectKey) throws Exception {
        minioClient.removeObject(RemoveObjectArgs.builder()
                .bucket(properties.getMinio().getBucket())
                .object(objectKey)
                .build());
    }

    @Override
    public String signUrl(String objectKey, int expireSeconds) throws Exception {
        return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .method(io.minio.http.Method.GET)
                .bucket(properties.getMinio().getBucket())
                .object(objectKey)
                .expiry(expireSeconds)
                .build());
    }

    @Override
    public boolean exists(String objectKey) {
        try {
            minioClient.statObject(StatObjectArgs.builder()
                    .bucket(properties.getMinio().getBucket())
                    .object(objectKey)
                    .build());
            return true;
        } catch (ErrorResponseException e) {
            return false;
        } catch (Exception e) {
            log.warn("MinIO exists 检查异常 objectKey={}, err={}", objectKey, e.getMessage());
            return false;
        }
    }
}
