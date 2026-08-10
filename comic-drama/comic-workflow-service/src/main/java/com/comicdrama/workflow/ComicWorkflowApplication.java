package com.comicdrama.workflow;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 工作流服务：7 步流水线中间产物。
 * Phase-1 仅 scaffold CRUD；Phase-2 接入 AbstractStepHandler + 7 handler + 四个 AI 客户端。
 */
@SpringBootApplication(scanBasePackages = "com.comicdrama")
@MapperScan("com.comicdrama.workflow.mapper")
public class ComicWorkflowApplication {

    public static void main(String[] args) {
        SpringApplication.run(ComicWorkflowApplication.class, args);
    }
}
