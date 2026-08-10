package com.comicdrama.workflow.handler;

import com.comicdrama.common.broadcast.MessageBroadcaster;
import com.comicdrama.common.service.TaskPauseChecker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 已弃用的素材提示词步骤处理器。
 * 功能已整合到资产设计（ASSET_DESIGN）步骤中。
 * 保留此处理器仅用于向后兼容，实际不执行任何操作。
 */
@Slf4j
@Component
@Deprecated
public class MaterialPromptStepHandler extends AbstractStepHandler {

    public MaterialPromptStepHandler(List<com.comicdrama.common.ai.AiModelInvoker> invokers,
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
        // 返回null以表示此处理器已弃用，不会被工作流引擎调用
        return null;
    }

    @Override
    protected void doExecute(StepContext context) throws Exception {
        log.warn("[MaterialPromptStepHandler] 此处理器已弃用，不应被调用");
    }
}
