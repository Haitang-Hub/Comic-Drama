package com.comicdrama.workflow.dto;

import com.comicdrama.common.dto.TaskCreateDTO;
import lombok.Data;

import java.util.Map;

/**
 * 流水线执行请求 DTO。
 * 由 task-service 通过 HTTP 发送到 workflow-service 触发流水线执行。
 */
@Data
public class PipelineExecuteRequest {

    private Long taskId;

    private Long userId;

    private String title;

    private TaskCreateDTO taskCreateDTO;

    /** 最大执行步骤数（默认3，仅执行文本阶段） */
    private Integer maxSteps = 3;

    /** 单图重生成时的参数覆盖（如 assetDesc、artStyle、visualStyle、visualDesc） */
    private Map<String, Object> overrides;
}