package com.comicdrama.gateway;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.comicdrama.common.broadcast.ApplicationEventMessageBroadcaster;
import com.comicdrama.common.satoken.SaTokenJwtConfig;
import com.comicdrama.common.satoken.StpInterfaceImpl;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * API 网关（Spring Cloud Gateway + Sa-Token Reactor）。
 * 仅 @Import JWT 配置与 StpInterface，避免加载 WebMVC/MyBatis 等不适用的 common 组件。
 * 排除 DataSource/MyBatis 自动配置（网关不访问数据库，common 传递引入了 mybatis-plus starter）。
 */
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class, MybatisPlusAutoConfiguration.class})
@Import({SaTokenJwtConfig.class, StpInterfaceImpl.class, ApplicationEventMessageBroadcaster.class})
public class ComicGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ComicGatewayApplication.class, args);
    }
}
