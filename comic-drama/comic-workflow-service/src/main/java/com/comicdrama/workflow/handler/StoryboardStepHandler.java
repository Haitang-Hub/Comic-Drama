package com.comicdrama.workflow.handler;

import com.comicdrama.common.ai.AiInvokeRequest;
import com.comicdrama.common.ai.AiInvokeResponse;
import com.comicdrama.common.broadcast.MessageBroadcaster;
import com.comicdrama.common.exception.BizException;
import com.comicdrama.common.service.TaskPauseChecker;
import com.comicdrama.workflow.entity.StorySummary;
import com.comicdrama.workflow.entity.Storyboard;
import com.comicdrama.workflow.service.StoryboardService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * STORYBOARD 步骤处理器：分镜脚本生成（步骤2）。
 * 根据故事摘要生成完整的分镜脚本。
 */
@Slf4j
@Component
public class StoryboardStepHandler extends AbstractStepHandler {

    private final StoryboardService storyboardService;

    public StoryboardStepHandler(List<com.comicdrama.common.ai.AiModelInvoker> invokers,
                                 AiModelConfigProvider modelConfigProvider,
                                 PromptTemplateProvider promptTemplateProvider,
                                 TaskProgressRecorder progressRecorder,
                                 TaskFailureRecorder failureRecorder,
                                 MessageBroadcaster broadcaster,
                                 StepModelBindingResolver bindingResolver,
                                 TokenUsageRecorder tokenUsageRecorder,
                                 StoryboardService storyboardService,
                                 TaskPauseChecker pauseChecker) {
        super(invokers, modelConfigProvider, promptTemplateProvider, progressRecorder, failureRecorder, broadcaster, bindingResolver, tokenUsageRecorder, pauseChecker);
        this.storyboardService = storyboardService;
    }

    @Override
    public StepEnum getStep() {
        return StepEnum.STORYBOARD;
    }

    @Override
    protected void preCheck(StepContext context) {
        StorySummary summary = context.getArtifact(StepEnum.SUMMARY);
        if (summary == null || !StringUtils.hasText(summary.getContent())) {
            throw new BizException("[STORYBOARD] 前置步骤[SUMMARY]产物缺失，无法生成分镜");
        }
    }

    @Override
    protected void doExecute(StepContext context) throws Exception {
        StorySummary summary = context.getArtifact(StepEnum.SUMMARY);

        log.info("[STORYBOARD] 开始生成分镜，summaryId={}, taskId={}", summary.getId(), context.getTaskId());

        reportProgress(context, 10, "正在加载 Prompt 模板...");

        String template = loadPromptTemplate("storyboard");
        String filledPrompt = fillTemplate(template,
                "summary", summary.getContent(),
                "duration", String.valueOf(context.getRequestDTO().getDuration()));

        reportProgress(context, 30, "正在调用 AI 生成分镜脚本...");

        AiInvokeRequest request = AiInvokeRequest.builder()
                .modelProvider(getStep().getModelProvider())
                .nodeKey("storyboard")
                .prompt(filledPrompt)
                .build();

        AiInvokeResponse response = invokeByModel(context, request);

        if (!response.isSuccess()) {
            throw new BizException("分镜脚本生成失败：" + response.getErrorMessage());
        }

        reportProgress(context, 70, "正在解析分镜脚本...");

        List<Storyboard> storyboards = parseStoryboards(response.getText(), context);

        if (storyboards.isEmpty()) {
            throw new BizException("分镜脚本解析结果为空，无法继续执行后续步骤");
        }

        log.info("[STORYBOARD] 分镜正在upsert保存，总数={}", storyboards.size());
        int insertCnt = 0, updateCnt = 0;
        for (Storyboard sb : storyboards) {
            Storyboard existed = storyboardService.lambdaQuery()
                    .eq(Storyboard::getTaskId, sb.getTaskId())
                    .eq(Storyboard::getSeq, sb.getSeq())
                    .one();
            if (existed != null) {
                sb.setId(existed.getId());
                storyboardService.updateById(sb);
                updateCnt++;
            } else {
                storyboardService.save(sb);
                insertCnt++;
            }
        }
        log.info("[STORYBOARD] 分镜已upsert完成，insert={}, update={}", insertCnt, updateCnt);

        context.putArtifact(StepEnum.STORYBOARD, storyboards);

        reportProgress(context, 100, "分镜脚本生成完成");
        log.info("[STORYBOARD] 分镜脚本生成成功，totalCount={}, taskId={}",
                storyboards.size(), context.getTaskId());
    }

    private List<Storyboard> parseStoryboards(String text, StepContext context) throws Exception {
        // 提取分镜脚本内容（剥离 Markdown 围栏，提示词不再要求 <<< >>> 包裹）
        String extracted = extractOutputContent(text);
        List<Storyboard> storyboards = CsvStoryboardParser.parse(
                extracted, context.getTaskId(), 0);

        if (storyboards.isEmpty()) {
            log.warn("[STORYBOARD] CSV解析结果为空，textLength={}", extracted.length());
        }

        return storyboards;
    }
}
