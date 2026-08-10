package com.comicdrama.auth.controller;

import com.comicdrama.auth.entity.SysUser;
import com.comicdrama.auth.service.SysUserService;
import com.comicdrama.auth.vo.SysUserVO;
import com.comicdrama.common.dto.PageQuery;
import com.comicdrama.common.result.PageResult;
import com.comicdrama.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/sys/user")
@RequiredArgsConstructor
public class SysUserController {

    private final SysUserService sysUserService;

    @GetMapping("/page")
    public Result<PageResult<SysUserVO>> page(@Valid PageQuery query,
                                              @RequestParam(required = false) String keyword) {
        return Result.ok(sysUserService.page(query, keyword));
    }

    @GetMapping("/{id}")
    public Result<SysUser> get(@PathVariable Long id) {
        SysUser user = sysUserService.getById(id);
        if (user != null) {
            user.setPassword(null);
        }
        return Result.ok(user);
    }

    @PostMapping
    public Result<Void> add(@RequestBody SysUser user) {
        if (user.getPassword() != null && !user.getPassword().isBlank()) {
            user.setPassword(cn.hutool.crypto.digest.BCrypt.hashpw(user.getPassword(), cn.hutool.crypto.digest.BCrypt.gensalt()));
        }
        sysUserService.save(user);
        return Result.ok();
    }

    @PutMapping
    public Result<Void> update(@RequestBody SysUser user) {
        // 不在此处更新密码
        user.setPassword(null);
        sysUserService.updateById(user);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        sysUserService.removeById(id);
        return Result.ok();
    }

    @PutMapping("/{id}/password")
    public Result<Void> resetPassword(@PathVariable Long id, @RequestParam String newPassword) {
        sysUserService.resetPassword(id, newPassword);
        return Result.ok();
    }

    @PutMapping("/{id}/roles")
    public Result<Void> assignRoles(@PathVariable Long id, @RequestBody List<Long> roleIds) {
        sysUserService.assignRoles(id, roleIds);
        return Result.ok();
    }
}
