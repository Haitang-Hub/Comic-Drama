package com.comicdrama.eureka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * 服务注册中心（单节点，开发期）。
 */
@EnableEurekaServer
@SpringBootApplication
public class ComicEurekaApplication {

    public static void main(String[] args) {
        SpringApplication.run(ComicEurekaApplication.class, args);
    }
}
