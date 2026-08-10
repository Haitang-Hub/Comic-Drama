package com.comicdrama.workflow.controller;

import com.comicdrama.common.result.Result;
import com.comicdrama.workflow.entity.StepModelBinding;
import com.comicdrama.workflow.handler.StepEnum;
import com.comicdrama.workflow.handler.StepModelBindingResolver;
import com.comicdrama.workflow.mapper.StepModelBindingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/admin/step-binding")
@RequiredArgsConstructor
public class StepModelBindingController {

    private final StepModelBindingMapper stepModelBindingMapper;
    private final StepModelBindingResolver bindingResolver;

    @GetMapping("/list")
    public Result<List<StepModelBinding>> list() {
        return Result.ok(stepModelBindingMapper.selectAllOrdered());
    }

    @GetMapping("/{id}")
    public Result<StepModelBinding> get(@PathVariable Long id) {
        return Result.ok(stepModelBindingMapper.selectById(id));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody StepModelBinding binding) {
        binding.setId(id);
        binding.setUpdateTime(LocalDateTime.now());
        stepModelBindingMapper.updateById(binding);
        refreshBindingCache(binding);
        return Result.ok();
    }

    @PutMapping("/batch")
    public Result<Void> batchUpdate(@RequestBody List<StepModelBinding> bindings) {
        LocalDateTime now = LocalDateTime.now();
        for (StepModelBinding binding : bindings) {
            binding.setUpdateTime(now);
            stepModelBindingMapper.updateById(binding);
            refreshBindingCache(binding);
        }
        return Result.ok();
    }

    /**
     * 刷新步骤-模型绑定内存缓存。
     * StepModelBindingResolver 启动时全量加载到内存 Map，resolveModelConfig 只读缓存不查库，
     * 任何写操作后必须 refreshStep，否则任务执行用的是旧缓存。
     */
    private void refreshBindingCache(StepModelBinding binding) {
        if (binding == null || binding.getStepCode() == null) {
            return;
        }
        try {
            bindingResolver.refreshStep(StepEnum.valueOf(binding.getStepCode()));
        } catch (IllegalArgumentException e) {
            log.warn("刷新绑定缓存失败：未知 stepCode={}", binding.getStepCode());
        }
    }
}
