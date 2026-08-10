package com.comicdrama.resource.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.comicdrama.common.dto.PageQuery;
import com.comicdrama.common.result.PageResult;
import com.comicdrama.resource.entity.ComicWork;

public interface ComicWorkService extends IService<ComicWork> {

    ComicWork createWork(Long taskId, String title, String coverUrl, String finalVideoUrl,
                         String resolution, Integer duration);

    ComicWork getByTaskId(Long taskId);

    PageResult<ComicWork> page(PageQuery query, String keyword, Integer status, Long userId);

    ComicWork getById(Long id);

    void delete(Long id);
}
