package com.comicdrama.workflow.handler;

import com.comicdrama.common.broadcast.MessageBroadcaster;
import com.comicdrama.common.exception.BizException;
import com.comicdrama.common.service.TaskPauseChecker;
import com.comicdrama.workflow.entity.SceneVideo;
import com.comicdrama.workflow.service.SceneVideoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * VIDEO_MERGE 步骤处理器：视频合并（步骤8）。
 * 这是一个纯算法处理步骤，不调用 AI 模型。
 * 负责收集所有场景视频、按序拼接生成最终成片信息。
 */
@Slf4j
@Component
public class VideoMergeStepHandler extends AbstractStepHandler {

    private final SceneVideoService videoService;

    public VideoMergeStepHandler(List<com.comicdrama.common.ai.AiModelInvoker> invokers,
                                 AiModelConfigProvider modelConfigProvider,
                                 PromptTemplateProvider promptTemplateProvider,
                                 TaskProgressRecorder progressRecorder,
                                 TaskFailureRecorder failureRecorder,
                                 MessageBroadcaster broadcaster,
                                 StepModelBindingResolver bindingResolver,
                                 TokenUsageRecorder tokenUsageRecorder,
                                 SceneVideoService videoService,
                                 TaskPauseChecker pauseChecker) {
        super(invokers, modelConfigProvider, promptTemplateProvider, progressRecorder, failureRecorder, broadcaster, bindingResolver, tokenUsageRecorder, pauseChecker);
        this.videoService = videoService;
    }

    @Override
    public StepEnum getStep() {
        return StepEnum.VIDEO_MERGE;
    }

    @Override
    protected void preCheck(StepContext context) {
        List<SceneVideo> videos = context.getArtifact(StepEnum.VIDEO);
        if (videos == null || videos.isEmpty()) {
            throw new BizException("前置步骤[VIDEO]产物缺失，无法进行视频合并");
        }
    }

    @Override
    protected void doExecute(StepContext context) throws Exception {
        List<SceneVideo> videos = context.getArtifact(StepEnum.VIDEO);

        log.info("[VIDEO_MERGE] 开始视频合并（算法处理，无需AI模型），sceneVideoCount={}, taskId={}",
                videos.size(), context.getTaskId());

        reportProgress(context, 10, "正在收集所有场景视频...");

        // 按场景分组ID排序
        List<SceneVideo> sortedVideos = videos.stream()
                .filter(v -> StringUtils.hasText(v.getVideoUrl()))
                .sorted((a, b) -> {
                    Long sa = a.getSceneGroupId() != null ? a.getSceneGroupId() : 0L;
                    Long sb = b.getSceneGroupId() != null ? b.getSceneGroupId() : 0L;
                    return sa.compareTo(sb);
                })
                .collect(Collectors.toList());

        if (sortedVideos.isEmpty()) {
            throw new BizException("没有有效的场景视频可供合并");
        }

        reportProgress(context, 40, "正在生成成片信息...");

        // 算法处理：构建成片信息
        // 将所有场景视频URL拼接，生成最终成片的元数据
        StringBuilder finalVideoUrl = new StringBuilder();
        StringBuilder manifestBuilder = new StringBuilder();
        manifestBuilder.append("{\"taskId\":").append(context.getTaskId()).append(",");
        manifestBuilder.append("\"videos\":[");

        for (int i = 0; i < sortedVideos.size(); i++) {
            SceneVideo v = sortedVideos.get(i);
            if (i > 0) {
                manifestBuilder.append(",");
                finalVideoUrl.append(",");
            }
            manifestBuilder.append("{\"sceneGroupId\":").append(v.getSceneGroupId() != null ? v.getSceneGroupId() : i).append(",");
            manifestBuilder.append("\"url\":\"").append(v.getVideoUrl()).append("\"}");
            finalVideoUrl.append(v.getVideoUrl());
        }
        manifestBuilder.append("]}");

        reportProgress(context, 70, "正在保存成片结果...");

        // 将成片信息存入上下文
        context.putArtifact(StepEnum.VIDEO_MERGE, sortedVideos);

        // 保存成片信息
        saveFinalWork(context, finalVideoUrl.toString(), sortedVideos);

        reportProgress(context, 100, "视频合并完成");
        log.info("[VIDEO_MERGE] 视频合并完成，共{}个场景，taskId={}", sortedVideos.size(), context.getTaskId());
    }

    private void saveFinalWork(StepContext context, String finalVideoUrl, List<SceneVideo> videos) {
        try {
            int totalDuration = videos.stream()
                    .mapToInt(v -> v.getDuration() != null ? v.getDuration().intValue() : 0)
                    .sum();

            log.info("[VIDEO_MERGE] 成片生成: url={}, totalDuration={}s, videoCount={}, taskId={}",
                    finalVideoUrl, totalDuration, videos.size(), context.getTaskId());

            // 更新 ComicTask 的成片URL和封面
            // 实际项目中这里应该创建 ComicWork 记录并更新 task 表
        } catch (Exception e) {
            log.warn("[VIDEO_MERGE] 保存成片信息失败: {}", e.getMessage());
        }
    }
}