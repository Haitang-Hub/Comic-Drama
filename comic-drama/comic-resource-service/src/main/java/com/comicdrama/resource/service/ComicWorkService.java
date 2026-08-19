package com.comicdrama.resource.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.comicdrama.common.dto.PageQuery;
import com.comicdrama.common.result.PageResult;
import com.comicdrama.resource.entity.ComicWork;

public interface ComicWorkService extends IService<ComicWork> {

    ComicWork createWork(Long taskId, String title, String coverUrl, String finalVideoUrl, String primaryVideoUrl,
                         String resolution, Integer duration, Long userId);

    ComicWork getByTaskId(Long taskId);

    PageResult<ComicWork> page(PageQuery query, String keyword, Integer status, Long userId);

    ComicWork getById(Long id);

    String generateShareToken(Long id, int expireHours);

    ComicWork getByShareToken(String token);

    void incrementViewCount(Long id);

    /**
     * 按需（懒）打包成片ZIP包并返回签名下载URL。
     * 缓存命中直接返回；缓存未命中则实时：构建manifest → 下载场景视频 → 打包ZIP → 上传存储 → 回填DB → 返回签名URL。
     *
     * @param workOrTaskId 作品ID（comic_work.id）或任务ID（comic_task.id，懒创建作品场景）
     * @return ZIP 包签名下载 URL
     */
    String buildAndGetZipDownloadUrl(Long workOrTaskId);
}
