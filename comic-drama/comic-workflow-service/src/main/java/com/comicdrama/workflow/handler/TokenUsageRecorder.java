package com.comicdrama.workflow.handler;

import com.comicdrama.common.ai.AiInvokeRequest;
import com.comicdrama.common.ai.AiInvokeResponse;
import com.comicdrama.common.ai.AiModelContext;

/**
 * Token 用量日志记录器。
 * 将每次 AI 模型调用的输入/输出内容、耗时、状态等写入 token_usage_log 表。
 * 实现需保证记录失败不影响工作流主流程（吞异常仅打印日志）。
 */
public interface TokenUsageRecorder {

    /**
     * 记录一次 AI 模型调用。
     *
     * @param context      步骤执行上下文（含 taskId/userId）
     * @param step         当前步骤枚举
     * @param modelContext 模型配置上下文（含 modelName/modelType）
     * @param request      AI 调用请求（含 prompt/systemPrompt/nodeKey）
     * @param response     AI 调用响应（含 text/resourceUrl/costMs/status/errorMessage）
     */
    void record(StepContext context, StepEnum step,
                AiModelContext modelContext, AiInvokeRequest request,
                AiInvokeResponse response);
}
