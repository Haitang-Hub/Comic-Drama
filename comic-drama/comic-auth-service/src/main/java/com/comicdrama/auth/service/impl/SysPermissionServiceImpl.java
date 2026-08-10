package com.comicdrama.auth.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.comicdrama.auth.entity.SysPermission;
import com.comicdrama.auth.mapper.SysPermissionMapper;
import com.comicdrama.auth.service.SysPermissionService;
import org.springframework.stereotype.Service;

@Service
public class SysPermissionServiceImpl extends ServiceImpl<SysPermissionMapper, SysPermission> implements SysPermissionService {
}
