package com.comicdrama.common.constant;

/** 安全相关常量 */
public final class SecurityConstants {

    private SecurityConstants() {}

    /** Sa-Token Header / 参数名 */
    public static final String TOKEN_NAME = "Authorization";

    /** Bearer 前缀 */
    public static final String TOKEN_PREFIX = "Bearer ";

    /** 网关白名单（与 gateway application.yml 保持一致） */
    public static final String[] WHITE_LIST = {
            "/auth/login",
            "/auth/register",
            "/actuator/**",
            "/eureka/**",
            "/favicon.ico",
            "/doc.html",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/swagger-resources/**",
            "/webjars/**",
            "/ws/**",
            "/static/**"
    };

    /** 需 ADMIN 角色的路径前缀 */
    public static final String[] ADMIN_PATH_PREFIX = {
            "/api/sys/",
            "/api/system/",
            "/api/template/"
    };

    /** 角色编码 */
    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_USER = "USER";

    /** Session 键 */
    public static final String SESSION_USER_INFO = "userInfo";
    public static final String SESSION_ROLES = "roles";
    public static final String SESSION_PERMISSIONS = "permissions";
}
