package com.comicdrama.task.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.comicdrama.common.entity.BaseCreateTimeEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 任务失败日志表（异常容错、断点续跑）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("task_failure_log")
public class TaskFailureLog extends BaseCreateTimeEntity {

    private Long taskId;

    private Integer step;

    private String nodeType;

    private String nodeKey;

    private String modelName;

    private String errorType;

    private String errorMessage;

    private String errorStack;

    private String requestPayload;

    private String responsePayload;

    private Integer retryCount;

    private Integer resolved;

    private LocalDateTime resolvedTime;
}
