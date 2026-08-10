package com.comicdrama.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.comicdrama.common.dto.PageQuery;
import com.comicdrama.common.entity.BaseTimeEntity;
import com.comicdrama.common.result.PageResult;

import java.util.List;

/**
 * 工作流产物通用 Service 基类（Phase-1 scaffold CRUD）。
 * Phase-2 各 handler 会按需覆写/扩展专门逻辑。
 */
public abstract class AbstractWorkflowService<M extends BaseMapper<E>, E extends BaseTimeEntity>
        extends ServiceImpl<M, E> {

    /** 按任务ID列出全部产物 */
    public List<E> listByTaskId(Long taskId) {
        return this.list(new QueryWrapper<E>().eq("task_id", taskId));
    }

    /**
     * 按任务ID删除全部产物（物理删除）。
     * 用于任务重新执行时清除旧产物，避免唯一键冲突和重复数据。
     */
    public boolean deleteByTaskId(Long taskId) {
        return this.remove(new QueryWrapper<E>().eq("task_id", taskId));
    }

    /** 分页（按 taskId 可选过滤） */
    public PageResult<E> page(PageQuery query, Long taskId) {
        QueryWrapper<E> wrapper = new QueryWrapper<E>().orderByDesc("create_time");
        if (taskId != null) {
            wrapper.eq("task_id", taskId);
        }
        Page<E> page = new Page<>(query.getPage(), query.getSize());
        Page<E> result = this.page(page, wrapper);
        return new PageResult<E>(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }
}
