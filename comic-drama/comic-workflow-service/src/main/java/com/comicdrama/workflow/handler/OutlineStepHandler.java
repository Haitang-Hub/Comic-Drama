package com.comicdrama.workflow.handler;

import com.comicdrama.common.ai.AiModelInvoker;
import com.comicdrama.common.broadcast.MessageBroadcaster;
import com.comicdrama.common.service.TaskPauseChecker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 已弃用：SUMMARY 步骤处理器已由 {@link SummaryStepHandler} 替代。
 * 此 handler 不再参与工作流执行。
 */
@Slf4j
@Component
@Deprecated
public class OutlineStepHandler extends AbstractStepHandler {

    public OutlineStepHandler(List<AiModelInvoker> invokers,
                              AiModelConfigProvider modelConfigProvider,
                              PromptTemplateProvider promptTemplateProvider,
                              TaskProgressRecorder progressRecorder,
                              TaskFailureRecorder failureRecorder,
                              MessageBroadcaster broadcaster,
                              StepModelBindingResolver bindingResolver,
                              TokenUsageRecorder tokenUsageRecorder,
                              TaskPauseChecker pauseChecker) {
        super(invokers, modelConfigProvider, promptTemplateProvider, progressRecorder, failureRecorder, broadcaster, bindingResolver, tokenUsageRecorder, pauseChecker);
    }

    @Override
    public StepEnum getStep() {
        return null;
    }

    @Override
    protected void doExecute(StepContext context) {
        log.warn("[OutlineStepHandler] 此处理器已弃用，不应被调用");
    }
}
