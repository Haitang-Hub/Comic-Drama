package com.comicdrama.resource.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.comicdrama.common.dto.PageQuery;
import com.comicdrama.common.result.PageResult;
import com.comicdrama.resource.entity.ResourceCleanupLog;

public interface ResourceCleanupLogService extends IService<ResourceCleanupLog> {
    PageResult<ResourceCleanupLog> page(PageQuery query, Long taskId);
}
