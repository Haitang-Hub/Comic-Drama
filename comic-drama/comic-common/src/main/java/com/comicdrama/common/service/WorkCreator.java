package com.comicdrama.common.service;

/**
 * 作品创建器接口。
 * 负责在任务完成后创建 ComicWork 记录。
 */
public interface WorkCreator {

    /**
     * 创建 ComicWork 记录。
     *
     * @param taskId  任务 ID
     * @param userId  用户 ID
     * @param title   作品标题
     * @return 创建的作品 ID
     */
    Long createComicWork(Long taskId, Long userId, String title);
}