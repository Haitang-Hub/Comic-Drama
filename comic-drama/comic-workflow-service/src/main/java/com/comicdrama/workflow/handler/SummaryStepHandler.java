package com.comicdrama.workflow.handler;

import com.comicdrama.common.ai.AiInvokeRequest;
import com.comicdrama.common.ai.AiInvokeResponse;
import com.comicdrama.common.broadcast.MessageBroadcaster;
import com.comicdrama.common.exception.BizException;
import com.comicdrama.common.service.TaskPauseChecker;
import com.comicdrama.workflow.entity.StorySummary;
import com.comicdrama.workflow.service.StorySummaryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * SUMMARY 步骤处理器：生成故事摘要（步骤1）。
 */
@Slf4j
@Component
public class SummaryStepHandler extends AbstractStepHandler {

    private final StorySummaryService storySummaryService;

    public SummaryStepHandler(List<com.comicdrama.common.ai.AiModelInvoker> invokers,
                              AiModelConfigProvider modelConfigProvider,
                              PromptTemplateProvider promptTemplateProvider,
                              TaskProgressRecorder progressRecorder,
                              TaskFailureRecorder failureRecorder,
                              MessageBroadcaster broadcaster,
                              StepModelBindingResolver bindingResolver,
                              TokenUsageRecorder tokenUsageRecorder,
                              StorySummaryService storySummaryService,
                              TaskPauseChecker pauseChecker) {
        super(invokers, modelConfigProvider, promptTemplateProvider, progressRecorder, failureRecorder, broadcaster, bindingResolver, tokenUsageRecorder, pauseChecker);
        this.storySummaryService = storySummaryService;
    }

    @Override
    public StepEnum getStep() {
        return StepEnum.SUMMARY;
    }

    @Override
    protected void doExecute(StepContext context) throws Exception {
        log.info("[SUMMARY] 开始生成故事摘要，title={}, taskId={}",
                context.getRequestDTO().getTitle(), context.getTaskId());

        reportProgress(context, 10, "正在加载 Prompt 模板...");

        String template = loadPromptTemplate("summary");
        String filledPrompt = fillTemplate(template,
                "story_requirement", context.getRequestDTO().getStoryRequirement(),
                "duration", String.valueOf(context.getRequestDTO().getDuration()),
                "art_style", context.getRequestDTO().getArtStyle() != null ? context.getRequestDTO().getArtStyle() : "",
                "visual_style", context.getRequestDTO().getVisualStyle() != null ? context.getRequestDTO().getVisualStyle() : "");

        reportProgress(context, 30, "正在调用 AI 生成故事摘要...");

        AiInvokeRequest request = AiInvokeRequest.builder()
                .modelProvider(getStep().getModelProvider())
                .nodeKey("summary")
                .prompt(filledPrompt)
                .build();

        AiInvokeResponse response = invokeByModel(context, request);

        if (!response.isSuccess()) {
            throw new BizException("摘要生成失败：" + response.getErrorMessage());
        }

        reportProgress(context, 70, "正在解析摘要结果...");

        StorySummary summary = parseAndSaveSummary(response.getText(), context);

        context.putArtifact(StepEnum.SUMMARY, summary);

        reportProgress(context, 100, "故事摘要生成完成");
        log.info("[SUMMARY] 摘要生成成功，summaryId={}, taskId={}", summary.getId(), context.getTaskId());
    }

    private StorySummary parseAndSaveSummary(String text, StepContext context) throws Exception {
        String cleaned = extractOutputContent(text);
        String content = cleaned;

        Long taskId = context.getTaskId();

        List<StorySummary> existing = storySummaryService.listByTaskId(taskId);
        StorySummary summary;

        if (existing != null && !existing.isEmpty()) {
            summary = existing.get(0);
            summary.setContent(content);
            summary.setDuration(context.getRequestDTO().getDuration());
            storySummaryService.updateById(summary);
            log.info("[SUMMARY] 摘要已更新，id={}, contentLen={}",
                    summary.getId(), summary.getContent().length());
        } else {
            summary = new StorySummary();
            summary.setTaskId(taskId);
            summary.setContent(content);
            summary.setDuration(context.getRequestDTO().getDuration());
            storySummaryService.save(summary);
            log.info("[SUMMARY] 摘要已创建，id={}, contentLen={}",
                    summary.getId(), summary.getContent().length());
        }

        return summary;
    }
}
