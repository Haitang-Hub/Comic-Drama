package com.comicdrama.workflow.handler;

import com.comicdrama.common.ai.AiInvokeRequest;
import com.comicdrama.common.ai.AiInvokeResponse;
import com.comicdrama.common.ai.AiModelContext;
import com.comicdrama.workflow.entity.TokenUsageLog;
import com.comicdrama.workflow.service.TokenUsageLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 默认 Token 用量日志记录器实现。
 * 组装 {@link TokenUsageLog} 并持久化，记录失败仅打印日志、不抛异常，避免影响工作流主流程。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultTokenUsageRecorder implements TokenUsageRecorder {

    private final TokenUsageLogService tokenUsageLogService;

    @Override
    public void record(StepContext context, StepEnum step,
                       AiModelContext modelContext, AiInvokeRequest request,
                       AiInvokeResponse response) {
        try {
            TokenUsageLog logEntity = new TokenUsageLog();
            logEntity.setTaskId(context.getTaskId());
            logEntity.setUserId(context.getUserId());
            logEntity.setStep(step.getOrder());
            logEntity.setNodeType(request.getNodeKey());
            logEntity.setModelName(modelContext.getModelName());
            logEntity.setModelType(modelContext.getModelType());
            logEntity.setInputContent(buildInputContent(request));
            logEntity.setOutputContent(buildOutputContent(response));
            logEntity.setLatencyMs(response.getCostMs() != null ? response.getCostMs().intValue() : 0);
            logEntity.setStatus(response.isSuccess() ? 1 : 0);
            logEntity.setErrorMsg(response.isSuccess() ? null : response.getErrorMessage());

            tokenUsageLogService.save(logEntity);
        } catch (Exception e) {
            log.error("保存 Token 用量日志失败：taskId={}, step={}, nodeKey={}",
                    context.getTaskId(), step.getOrder(), request.getNodeKey(), e);
        }
    }

    /** 组装完整输入文本：系统提示（若有）+ 用户输入。 */
    private String buildInputContent(AiInvokeRequest request) {
        String systemPrompt = request.getSystemPrompt();
        String prompt = request.getPrompt();
        if (StringUtils.hasText(systemPrompt)) {
            return "[系统提示]\n" + systemPrompt + "\n\n[用户输入]\n" + (prompt == null ? "" : prompt);
        }
        return prompt;
    }

    /** 组装输出内容：文本模型取 text，资源类模型取 resourceUrl，失败取错误信息。 */
    private String buildOutputContent(AiInvokeResponse response) {
        if (!response.isSuccess()) {
            return response.getErrorMessage();
        }
        if (StringUtils.hasText(response.getText())) {
            return response.getText();
        }
        if (StringUtils.hasText(response.getResourceUrl())) {
            return response.getResourceUrl();
        }
        return null;
    }
}
