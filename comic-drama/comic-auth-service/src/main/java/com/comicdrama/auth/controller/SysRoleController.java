package com.comicdrama.auth.controller;

import com.comicdrama.auth.entity.SysRole;
import com.comicdrama.auth.service.SysRoleService;
import com.comicdrama.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sys/role")
@RequiredArgsConstructor
public class SysRoleController {

    private final SysRoleService sysRoleService;

    @GetMapping("/list")
    public Result<List<SysRole>> list() {
        return Result.ok(sysRoleService.list());
    }

    @GetMapping("/{id}")
    public Result<SysRole> get(@PathVariable Long id) {
        return Result.ok(sysRoleService.getById(id));
    }

    @PostMapping
    public Result<Void> add(@RequestBody SysRole role) {
        sysRoleService.save(role);
        return Result.ok();
    }

    @PutMapping
    public Result<Void> update(@RequestBody SysRole role) {
        sysRoleService.updateById(role);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        sysRoleService.removeById(id);
        return Result.ok();
    }

    @PutMapping("/{id}/permissions")
    public Result<Void> assignPermissions(@PathVariable Long id, @RequestBody List<Long> permissionIds) {
        sysRoleService.assignPermissions(id, permissionIds);
        return Result.ok();
    }

    @GetMapping("/{id}/permissions")
    public Result<List<Long>> permissionIds(@PathVariable Long id) {
        return Result.ok(sysRoleService.getPermissionIds(id));
    }
}
