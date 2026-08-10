package com.comicdrama.common.satoken;

import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Sa-Token 权限/角色查询实现。
 * Phase-1 无状态 JWT：角色与权限在登录时写入 token extra，本类从 token 读取，
 * 各业务服务无需查库即可完成角色/权限校验。
 * Phase-5 引入 Redis 后，auth-service 可改为查库实现覆盖本默认行为。
 */
@Component
public class StpInterfaceImpl implements StpInterface {

    @Override
    @SuppressWarnings("unchecked")
    public List<String> getPermissionList(Object loginId, String loginType) {
        try {
            Object perms = StpUtil.getExtra("permissions");
            if (perms instanceof List) {
                return (List<String>) perms;
            }
        } catch (Exception ignored) {
            // token 未携带 extra 时返回空
        }
        return Collections.emptyList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> getRoleList(Object loginId, String loginType) {
        try {
            Object roles = StpUtil.getExtra("roles");
            if (roles instanceof List) {
                return (List<String>) roles;
            }
        } catch (Exception ignored) {
        }
        return Collections.emptyList();
    }
}
