package com.comicdrama.system;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 系统服务：AI模型配置 / 系统配置 / Prompt模板(版本回滚) / 操作日志 / 任务统计。
 * scanBasePackages=com.comicdrama 以加载 comic-common 的公共配置与组件。
 */
@SpringBootApplication(scanBasePackages = "com.comicdrama")
@MapperScan("com.comicdrama.system.mapper")
@EnableAsync
@EnableScheduling
public class ComicSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(ComicSystemApplication.class, args);
    }
}
