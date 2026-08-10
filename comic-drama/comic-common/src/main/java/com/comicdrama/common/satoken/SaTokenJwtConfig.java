package com.comicdrama.common.satoken;

import cn.dev33.satoken.jwt.StpLogicJwtForMixin;
import cn.dev33.satoken.stp.StpLogic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Sa-Token JWT 配置（Mixin 模式）。
 * 无 Redis 时的跨服务无状态鉴权方案：
 *   - token 自带 loginId + extra(roles/permissions)
 *   - 网关与各业务服务共享同一 jwt-secret-key 即可解析 token
 *   - login 时通过 SaLoginModel.setExtra 写入角色/权限
 * Phase-5 引入 Redis 后可切回默认模式（Session 共享）。
 */
@Configuration
public class SaTokenJwtConfig {

    @Bean
    public StpLogic stpLogicJwt() {
        return new StpLogicJwtForMixin();
    }
}
