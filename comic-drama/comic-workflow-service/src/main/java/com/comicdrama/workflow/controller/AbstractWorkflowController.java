package com.comicdrama.workflow.controller;

import com.comicdrama.common.dto.PageQuery;
import com.comicdrama.common.entity.BaseTimeEntity;
import com.comicdrama.common.result.PageResult;
import com.comicdrama.common.result.Result;
import com.comicdrama.workflow.service.AbstractWorkflowService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 工作流产物通用 CRUD 控制器基类（Phase-1 scaffold）。
 * 子类只需声明 @RestController + @RequestMapping + getService()。
 * Spring MVC 会继承本基类的 handler 方法。
 */
public abstract class AbstractWorkflowController<S extends AbstractWorkflowService<?, E>, E extends BaseTimeEntity> {

    protected abstract S getService();

    @GetMapping("/page")
    public Result<PageResult<E>> page(PageQuery query,
                                        @RequestParam(required = false) Long taskId) {
        return Result.ok(getService().page(query, taskId));
    }

    @GetMapping("/task/{taskId}")
    public Result<List<E>> listByTask(@PathVariable Long taskId) {
        return Result.ok(getService().listByTaskId(taskId));
    }

    @GetMapping("/{id}")
    public Result<E> get(@PathVariable Long id) {
        return Result.ok(getService().getById(id));
    }

    @PostMapping
    public Result<Void> add(@RequestBody E entity) {
        getService().save(entity);
        return Result.ok();
    }

    @PutMapping
    public Result<Void> update(@RequestBody E entity) {
        getService().updateById(entity);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        getService().removeById(id);
        return Result.ok();
    }
}
