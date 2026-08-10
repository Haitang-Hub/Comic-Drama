package com.comicdrama.common.storage;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 存储配置。
 * storage.type=local（默认）| minio
 */
@Data
@Component
@ConfigurationProperties(prefix = "storage")
public class StorageProperties {

    /** 存储类型：local / minio */
    private String type = "local";

    /** 本地存储根路径 */
    private String localBasePath = "./data/storage";

    /** 本地存储访问 URL 前缀（用于 signUrl 拼接，可选） */
    private String localBaseUrl = "http://127.0.0.1:8070/static";

    /** MinIO 配置 */
    private Minio minio = new Minio();

    @Data
    public static class Minio {
        private String endpoint = "http://127.0.0.1:9000";
        private String accessKey = "comic";
        private String secretKey = "comic12345";
        private String bucket = "comic-drama";
    }
}
