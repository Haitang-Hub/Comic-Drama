package com.comicdrama.gateway.config;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import cn.dev33.satoken.reactor.filter.SaReactorFilter;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import com.comicdrama.common.constant.SecurityConstants;
import com.comicdrama.common.result.Result;
import com.comicdrama.common.result.ResultCode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Sa-Token 网关鉴权配置（Reactor）。
 * - 全局校验登录态（白名单放行）
 * - 管理类路径校验 ADMIN 角色
 * - 鉴权失败统一返回 Result 结构
 */
@Configuration
public class SaTokenGatewayConfig {

    @Bean
    public SaReactorFilter saReactorFilter() {
        return new SaReactorFilter()
                // 拦截所有
                .addInclude("/**")
                // 静态/文档排除
                .addExclude("/favicon.ico", "/doc.html", "/swagger-ui/**", "/v3/api-doc/**", "/webjars/**", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-resources/**")
                .setAuth(obj -> {
                    // 全局登录校验（白名单放行）
                    SaRouter.match("/**")
                            .notMatch(SecurityConstants.WHITE_LIST)
                            .check(r -> StpUtil.checkLogin());
                    // 管理类路径需 ADMIN 角色
                    SaRouter.match(SecurityConstants.ADMIN_PATH_PREFIX)
                            .check(r -> StpUtil.checkRole(SecurityConstants.ROLE_ADMIN));
                })
                .setError(e -> toResult(e));
    }

    private Object toResult(Throwable e) {
        if (e instanceof NotLoginException) {
            return Result.fail(ResultCode.UNAUTHORIZED);
        }
        if (e instanceof NotRoleException || e instanceof NotPermissionException) {
            return Result.fail(ResultCode.FORBIDDEN);
        }
        return Result.fail(ResultCode.BIZ_ERROR, e.getMessage());
    }
}
