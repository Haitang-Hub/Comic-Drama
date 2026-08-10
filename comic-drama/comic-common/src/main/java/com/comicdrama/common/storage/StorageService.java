package com.comicdrama.common.storage;

import java.io.InputStream;

/**
 * 存储服务抽象（轻量中间件）。
 * Phase-1 默认 {@link LocalFileStorageService}（本地磁盘），
 * Phase-2 启用 MinIO 后切换 {@link MinioStorageService}（storage.type=minio）。
 */
public interface StorageService {

    /**
     * 上传文件
     *
     * @param is          输入流
     * @param objectKey   对象 key（相对路径，如 task/1001/image/xxx.png）
     * @param size        文件大小（字节，未知传 -1）
     * @param contentType MIME 类型
     * @return 对象 key（可用于 download/signUrl）
     */
    String upload(InputStream is, String objectKey, long size, String contentType) throws Exception;

    /** 下载 */
    InputStream download(String objectKey) throws Exception;

    /** 删除 */
    void delete(String objectKey) throws Exception;

    /**
     * 生成临时访问 URL（local 实现返回直接可访问 URL 或带 token 的 URL）
     *
     * @param objectKey     对象 key
     * @param expireSeconds 过期秒数
     */
    String signUrl(String objectKey, int expireSeconds) throws Exception;

    /** 是否存在 */
    boolean exists(String objectKey);
}
