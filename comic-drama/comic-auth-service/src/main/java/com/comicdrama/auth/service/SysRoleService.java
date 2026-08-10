package com.comicdrama.auth.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.comicdrama.auth.entity.SysRole;

import java.util.List;

public interface SysRoleService extends IService<SysRole> {

    void assignPermissions(Long roleId, List<Long> permissionIds);

    List<Long> getPermissionIds(Long roleId);
}
