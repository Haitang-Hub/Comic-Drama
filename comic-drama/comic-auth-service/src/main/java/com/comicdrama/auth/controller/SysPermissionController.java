package com.comicdrama.auth.controller;

import com.comicdrama.auth.entity.SysPermission;
import com.comicdrama.auth.service.SysPermissionService;
import com.comicdrama.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sys/permission")
@RequiredArgsConstructor
public class SysPermissionController {

    private final SysPermissionService sysPermissionService;

    @GetMapping("/list")
    public Result<List<SysPermission>> list() {
        return Result.ok(sysPermissionService.list());
    }

    @GetMapping("/{id}")
    public Result<SysPermission> get(@PathVariable Long id) {
        return Result.ok(sysPermissionService.getById(id));
    }

    @PostMapping
    public Result<Void> add(@RequestBody SysPermission permission) {
        sysPermissionService.save(permission);
        return Result.ok();
    }

    @PutMapping
    public Result<Void> update(@RequestBody SysPermission permission) {
        sysPermissionService.updateById(permission);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        sysPermissionService.removeById(id);
        return Result.ok();
    }
}
