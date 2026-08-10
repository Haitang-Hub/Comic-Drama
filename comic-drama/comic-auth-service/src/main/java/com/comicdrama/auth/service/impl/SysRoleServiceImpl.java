package com.comicdrama.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.comicdrama.auth.entity.SysRole;
import com.comicdrama.auth.entity.SysRolePermission;
import com.comicdrama.auth.mapper.SysRoleMapper;
import com.comicdrama.auth.mapper.SysRolePermissionMapper;
import com.comicdrama.auth.service.SysRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysRoleServiceImpl extends ServiceImpl<SysRoleMapper, SysRole> implements SysRoleService {

    private final SysRolePermissionMapper sysRolePermissionMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignPermissions(Long roleId, List<Long> permissionIds) {
        sysRolePermissionMapper.delete(new LambdaQueryWrapper<SysRolePermission>()
                .eq(SysRolePermission::getRoleId, roleId));
        if (CollectionUtils.isEmpty(permissionIds)) {
            return;
        }
        for (Long permId : permissionIds) {
            SysRolePermission rp = new SysRolePermission();
            rp.setRoleId(roleId);
            rp.setPermissionId(permId);
            sysRolePermissionMapper.insert(rp);
        }
    }

    @Override
    public List<Long> getPermissionIds(Long roleId) {
        return sysRolePermissionMapper.selectList(new LambdaQueryWrapper<SysRolePermission>()
                        .eq(SysRolePermission::getRoleId, roleId))
                .stream().map(SysRolePermission::getPermissionId).collect(Collectors.toList());
    }
}
