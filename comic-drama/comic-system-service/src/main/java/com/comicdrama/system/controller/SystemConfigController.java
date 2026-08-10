package com.comicdrama.system.controller;

import com.comicdrama.common.dto.PageQuery;
import com.comicdrama.common.result.PageResult;
import com.comicdrama.common.result.Result;
import com.comicdrama.system.entity.SystemConfig;
import com.comicdrama.system.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 系统配置管理（管理员）
 */
@RestController
@RequestMapping("/api/system/config")
@RequiredArgsConstructor
public class SystemConfigController {

    private final SystemConfigService systemConfigService;

    @GetMapping("/page")
    public Result<PageResult<SystemConfig>> page(PageQuery query,
                                                  @RequestParam(required = false) String keyword) {
        return Result.ok(systemConfigService.page(query, keyword));
    }

    @GetMapping("/list")
    public Result<List<SystemConfig>> list() {
        return Result.ok(systemConfigService.listEnabled());
    }

    @GetMapping("/value/{key}")
    public Result<String> getValue(@PathVariable String key) {
        return Result.ok(systemConfigService.getValue(key));
    }

    @GetMapping("/{id}")
    public Result<SystemConfig> get(@PathVariable Long id) {
        return Result.ok(systemConfigService.getById(id));
    }

    @PostMapping
    public Result<Void> add(@RequestBody SystemConfig config) {
        systemConfigService.save(config);
        return Result.ok();
    }

    @PutMapping
    public Result<Void> update(@RequestBody SystemConfig config) {
        systemConfigService.updateById(config);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        systemConfigService.delete(id);
        return Result.ok();
    }
}
