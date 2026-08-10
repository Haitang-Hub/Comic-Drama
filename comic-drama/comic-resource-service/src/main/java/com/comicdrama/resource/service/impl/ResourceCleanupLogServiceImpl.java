package com.comicdrama.resource.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.comicdrama.common.dto.PageQuery;
import com.comicdrama.common.result.PageResult;
import com.comicdrama.resource.entity.ResourceCleanupLog;
import com.comicdrama.resource.mapper.ResourceCleanupLogMapper;
import com.comicdrama.resource.service.ResourceCleanupLogService;
import org.springframework.stereotype.Service;

@Service
public class ResourceCleanupLogServiceImpl extends ServiceImpl<ResourceCleanupLogMapper, ResourceCleanupLog> implements ResourceCleanupLogService {

    @Override
    public PageResult<ResourceCleanupLog> page(PageQuery query, Long taskId) {
        LambdaQueryWrapper<ResourceCleanupLog> wrapper = new LambdaQueryWrapper<>();
        if (taskId != null) {
            wrapper.eq(ResourceCleanupLog::getTaskId, taskId);
        }
        wrapper.orderByDesc(ResourceCleanupLog::getCreateTime);
        Page<ResourceCleanupLog> page = new Page<>(query.getPage(), query.getSize());
        Page<ResourceCleanupLog> result = this.page(page, wrapper);
        return new PageResult<>(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }
}
