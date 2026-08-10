package com.comicdrama.common.util;

import cn.dev33.satoken.stp.StpUtil;

/**
 * 安全上下文工具：从 Sa-Token 获取当前登录用户。
 */
public final class SecurityUtils {

    private SecurityUtils() {}

    public static boolean isLogin() {
        try {
            return StpUtil.isLogin();
        } catch (Exception e) {
            return false;
        }
    }

    public static Long getCurrentUserId() {
        return StpUtil.getLoginIdAsLong();
    }

    public static Long getCurrentUserIdOrNull() {
        try {
            if (StpUtil.isLogin()) {
                return StpUtil.getLoginIdAsLong();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public static String getCurrentUsername() {
        try {
            return (String) StpUtil.getExtra("username");
        } catch (Exception ignored) {
            return null;
        }
    }
}
