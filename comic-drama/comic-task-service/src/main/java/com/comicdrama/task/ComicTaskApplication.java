package com.comicdrama.task;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 任务服务：任务/队列/进度/失败/节点状态。
 * Phase-1 端到端核心：创建任务 → 入队（内存队列 stub）→ 状态机推进。
 */
@SpringBootApplication(scanBasePackages = "com.comicdrama")
@MapperScan("com.comicdrama.task.mapper")
@EnableScheduling
@EnableFeignClients
public class ComicTaskApplication {

    public static void main(String[] args) {
        SpringApplication.run(ComicTaskApplication.class, args);
    }
}
