package com.comicdrama.auth;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 鉴权服务。
 * scanBasePackages=com.comicdrama 以加载 comic-common 的公共配置与组件。
 */
@SpringBootApplication(scanBasePackages = "com.comicdrama")
@MapperScan("com.comicdrama.auth.mapper")
public class ComicAuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(ComicAuthApplication.class, args);
    }
}
