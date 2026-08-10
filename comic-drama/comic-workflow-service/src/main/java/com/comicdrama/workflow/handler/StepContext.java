package com.comicdrama.workflow.handler;

import com.comicdrama.common.dto.TaskCreateDTO;
import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 步骤执行上下文。
 * 在流水线中传递任务信息、原始请求参数、已完成步骤的产物，以及当前进度。
 */
@Data
@Builder
public class StepContext {

    private Long taskId;

    private String taskNo;

    private Long userId;

    /** 原始任务创建请求 DTO（包含全部用户参数） */
    private TaskCreateDTO requestDTO;

    /** 已完成步骤的产物，key = 步骤枚举，value = 该步骤产出的实体或实体列表 */
    @Builder.Default
    private Map<StepEnum, Object> completedSteps = new HashMap<>();

    /** 当前执行到的步骤 */
    private StepEnum currentStep;

    /** 当前步骤进度（0-100） */
    private Integer progress;

    /** 流水线总进度（0-100） */
    private Integer totalProgress;

    /**
     * 是否跳过测试优先批量模式（用于断点续跑时直接进入批量阶段）。
     * 当从失败步骤恢复时，此值为 true，表示跳过 test-first 直接批量。
     */
    @Builder.Default
    private boolean skipTestBatch = false;

    /**
     * 单图重生成时的参数覆盖（如 assetDesc、artStyle、visualStyle 等）。
     * key = 参数名，value = 覆盖值。
     */
    @Builder.Default
    private Map<String, Object> overrides = new HashMap<>();

    /**
     * 获取已完成步骤的产物。
     */
    @SuppressWarnings("unchecked")
    public <T> T getArtifact(StepEnum step) {
        return (T) completedSteps.get(step);
    }

    /**
     * 存入当前步骤的产物，供后续步骤消费。
     */
    public void putArtifact(StepEnum step, Object artifact) {
        completedSteps.put(step, artifact);
    }
}
