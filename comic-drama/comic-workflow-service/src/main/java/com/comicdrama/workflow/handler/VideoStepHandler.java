package com.comicdrama.workflow.handler;

import com.comicdrama.common.ai.AiInvokeRequest;
import com.comicdrama.common.ai.AiInvokeResponse;
import com.comicdrama.common.broadcast.MessageBroadcaster;
import com.comicdrama.common.exception.BizException;
import com.comicdrama.common.service.TaskPauseChecker;
import com.comicdrama.workflow.entity.AssetImage;
import com.comicdrama.workflow.entity.SceneVideo;
import com.comicdrama.workflow.entity.Storyboard;
import com.comicdrama.workflow.entity.StoryboardAudio;
import com.comicdrama.workflow.entity.StoryboardImage;
import com.comicdrama.workflow.service.SceneVideoService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * VIDEO 步骤处理器：视频生成（步骤7）。
 * 按场景分组生成视频，并保证场景内帧间承接（用上一帧图片作为参考）。
 *
 * <p>Phase-5：支持测试优先+批量模式，先测试一条，成功后批量生成。</p>
 */
@Slf4j
@Component
public class VideoStepHandler extends AbstractStepHandler {

    private final SceneVideoService videoService;
    private final ObjectMapper objectMapper;

    public VideoStepHandler(List<com.comicdrama.common.ai.AiModelInvoker> invokers,
                            AiModelConfigProvider modelConfigProvider,
                            PromptTemplateProvider promptTemplateProvider,
                            TaskProgressRecorder progressRecorder,
                            TaskFailureRecorder failureRecorder,
                            MessageBroadcaster broadcaster,
                            StepModelBindingResolver bindingResolver,
                            TokenUsageRecorder tokenUsageRecorder,
                            TaskPauseChecker pauseChecker,
                            SceneVideoService videoService,
                            ObjectMapper objectMapper) {
        super(invokers, modelConfigProvider, promptTemplateProvider, progressRecorder, failureRecorder,
                broadcaster, bindingResolver, tokenUsageRecorder, pauseChecker);
        this.videoService = videoService;
        this.objectMapper = objectMapper;
    }

    @Override
    public StepEnum getStep() {
        return StepEnum.VIDEO;
    }

    @Override
    protected void preCheck(StepContext context) {
        List<StoryboardImage> images = context.getArtifact(StepEnum.STORYBOARD_IMAGE);
        List<Storyboard> storyboards = context.getArtifact(StepEnum.STORYBOARD);
        if (images == null || images.isEmpty()) {
            throw new BizException("[VIDEO] 前置步骤[STORYBOARD_IMAGE]产物缺失，无法生成视频");
        }
        if (storyboards == null || storyboards.isEmpty()) {
            throw new BizException("[VIDEO] 前置步骤[STORYBOARD]产物缺失，无法生成视频");
        }
    }

    @Override
    protected void doExecute(StepContext context) throws Exception {
        List<StoryboardImage> images = context.getArtifact(StepEnum.STORYBOARD_IMAGE);
        List<Storyboard> storyboards = context.getArtifact(StepEnum.STORYBOARD);

        log.info("[VIDEO] 开始生成视频，imageCount={}, storyboardCount={}, taskId={}",
                images.size(), storyboards.size(), context.getTaskId());

        reportProgress(context, 10, "正在加载 Prompt 模板并按场景分组...");

        context.putArtifact(StepEnum.VIDEO, new ArrayList<SceneVideo>());

        int successCount = doBatchExecute(context);

        List<SceneVideo> videos = context.getArtifact(StepEnum.VIDEO);
        if (videos == null) {
            videos = new ArrayList<>();
        }

        // 视频已在 saveBatchResult 中逐条 upsert，无需批量保存
        log.info("[VIDEO] 场景视频upsert完成，总数={}", videos.size());

        // 验证：如果有分镜但没有生成任何视频，抛出异常
        if (!storyboards.isEmpty() && videos.isEmpty()) {
            log.error("[VIDEO] 视频生成失败：有 {} 个分镜但未生成任何视频", storyboards.size());
            throw new BizException("[VIDEO] 视频生成失败：所有场景视频均生成失败");
        }

        reportProgress(context, 100, "视频生成完成");
        log.info("[VIDEO] 视频生成成功，videoCount={}, taskId={}", videos.size(), context.getTaskId());
    }

    @Override
    @SuppressWarnings("unchecked")
    protected <T> List<T> getBatchItems(StepContext context) {
        List<StoryboardImage> images = context.getArtifact(StepEnum.STORYBOARD_IMAGE);
        List<Storyboard> storyboards = context.getArtifact(StepEnum.STORYBOARD);

        String template = loadPromptTemplate("video");
        String artStyle = context.getRequestDTO().getArtStyle();
        String visualStyle = context.getRequestDTO().getVisualStyle();
        String aspectRatio = context.getRequestDTO().getAspectRatio();

        // 收集资产图片URL（步骤4+步骤5的所有资产图）
        List<AssetImage> assetImages = context.getArtifact(StepEnum.ASSET_IMAGE);
        StringBuilder assetImageUrls = new StringBuilder();
        if (assetImages != null) {
            for (AssetImage img : assetImages) {
                if (img != null && StringUtils.hasText(img.getImageUrl())) {
                    assetImageUrls.append(img.getImageUrl()).append(",");
                }
            }
        }
        if (assetImageUrls.length() > 0) {
            assetImageUrls.setLength(assetImageUrls.length() - 1);
        }

        // 收集配音文件URL（按storyboardId分组映射）
        List<StoryboardAudio> audios = context.getArtifact(StepEnum.AUDIO);
        Map<Long, String> audioByStoryboardId = new HashMap<>();
        if (audios != null) {
            for (StoryboardAudio audio : audios) {
                if (audio != null && audio.getStoryboardId() != null
                        && StringUtils.hasText(audio.getAudioUrl())) {
                    audioByStoryboardId.put(audio.getStoryboardId(), audio.getAudioUrl());
                }
            }
        }

        List<Storyboard> sortedSbs = storyboards.stream()
                .sorted((a, b) -> Integer.compare(a.getSeq(), b.getSeq()))
                .collect(Collectors.toList());

        Map<Long, StoryboardImage> imageByStoryboardId = new HashMap<>();
        for (StoryboardImage img : images) {
            if (img != null && img.getStoryboardId() != null && StringUtils.hasText(img.getImageUrl())) {
                imageByStoryboardId.put(img.getStoryboardId(), img);
            }
        }

        Map<Long, List<Storyboard>> byGroup = sortedSbs.stream()
                .collect(Collectors.groupingBy(sb -> sb.getGroupId() == null ? 0L : sb.getGroupId(),
                        java.util.LinkedHashMap::new,
                        Collectors.toList()));

        List<GroupBatchItem> groupItems = new ArrayList<>();
        for (Map.Entry<Long, List<Storyboard>> entry : byGroup.entrySet()) {
            // 收集当前分组的配音文件URL
            StringBuilder groupAudioUrls = new StringBuilder();
            for (Storyboard sb : entry.getValue()) {
                String audioUrl = audioByStoryboardId.get(sb.getId());
                if (audioUrl != null) {
                    groupAudioUrls.append(audioUrl).append(",");
                }
            }
            if (groupAudioUrls.length() > 0) {
                groupAudioUrls.setLength(groupAudioUrls.length() - 1);
            }

            groupItems.add(new GroupBatchItem(
                    entry.getKey(),
                    entry.getValue(),
                    template,
                    artStyle,
                    visualStyle,
                    aspectRatio,
                    imageByStoryboardId,
                    assetImageUrls.toString(),
                    groupAudioUrls.toString()
            ));
        }

        return (List<T>) groupItems;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T, R> R processBatchItem(T item, int index, StepContext context) throws Exception {
        GroupBatchItem groupItem = (GroupBatchItem) item;
        Long groupId = groupItem.groupId;
        List<Storyboard> groupSbs = groupItem.groupSbs;

        StringBuilder groupText = new StringBuilder();
        StringBuilder groupImageUrls = new StringBuilder();

        for (Storyboard sb : groupSbs) {
            groupText.append(String.format("[%d.%d] %s - %s%n",
                    sb.getSeq(), sb.getLocalSeq() == null ? 0 : sb.getLocalSeq(),
                    sb.getShotDesc(), sb.getVisualDesc()));
            StoryboardImage img = groupItem.imageByStoryboardId.get(sb.getId());
            if (img != null && StringUtils.hasText(img.getImageUrl())) {
                groupImageUrls.append(img.getImageUrl()).append(",");
            }
        }

        String filledPrompt = fillTemplate(groupItem.template,
                "storyboards", groupText.toString(),
                "storyboard_image", groupImageUrls.toString(),
                "asset_images", groupItem.assetImageUrls,
                "audio_files", groupItem.audioFileUrls,
                "art_style", StringUtils.hasText(groupItem.artStyle) ? groupItem.artStyle : "",
                "visual_style", StringUtils.hasText(groupItem.visualStyle) ? groupItem.visualStyle : "",
                "aspect_ratio", StringUtils.hasText(groupItem.aspectRatio) ? groupItem.aspectRatio : "16:9");

        AiInvokeRequest.AiInvokeRequestBuilder reqBuilder = AiInvokeRequest.builder()
                .modelProvider(getStep().getModelProvider())
                .nodeKey("video_g" + groupId)
                .prompt(filledPrompt);

        StoryboardImage firstImg = groupItem.imageByStoryboardId.get(groupSbs.get(0).getId());
        if (firstImg != null && StringUtils.hasText(firstImg.getImageUrl())) {
            reqBuilder.referenceImageUrl(firstImg.getImageUrl());
        }

        AiInvokeResponse response = invokeByModel(context, reqBuilder.build());

        if (!response.isSuccess()) {
            log.warn("[VIDEO] 场景{}视频生成失败：{}", groupId, response.getErrorMessage());
            return null;
        }

        SceneVideo video = new SceneVideo();
        video.setTaskId(context.getTaskId());
        video.setSceneGroupId(groupId);
        Integer startSeq = groupSbs.get(0).getSeq();
        Integer endSeq = groupSbs.get(groupSbs.size() - 1).getSeq();
        video.setStoryboardSeqRange(startSeq + "-" + endSeq);
        List<Long> sbIds = groupSbs.stream().map(Storyboard::getId).collect(Collectors.toList());
        video.setStoryboardIds(String.join(",", sbIds.stream().map(String::valueOf).collect(Collectors.toList())));
        video.setFrameCount(groupSbs.size());
        video.setBaseFrameUrl(firstImg != null ? firstImg.getImageUrl() : null);
        video.setVideoUrl(response.getResourceUrl());
        video.setDuration(BigDecimal.valueOf(
                groupSbs.stream().mapToDouble(sb -> sb.getDuration() == null ? 2.0 : sb.getDuration()).sum()));
        video.setStatus(1);

        log.info("[VIDEO] 场景{}视频生成成功，url={}", groupId, response.getResourceUrl());
        return (R) video;
    }

    @Override
    protected int resolveBatchStartIndex(StepContext context, int totalSize) {
        long existingCount = videoService.lambdaQuery()
                .eq(SceneVideo::getTaskId, context.getTaskId())
                .count();
        return (int) Math.min(existingCount, totalSize);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T, R> void saveBatchResult(T item, R result, StepContext context) {
        if (result == null) return;

        List<SceneVideo> videos = context.getArtifact(StepEnum.VIDEO);
        if (videos == null) {
            videos = new ArrayList<>();
            context.putArtifact(StepEnum.VIDEO, videos);
        }

        SceneVideo video = (SceneVideo) result;

        SceneVideo existed = videoService.lambdaQuery()
                .eq(SceneVideo::getTaskId, video.getTaskId())
                .eq(SceneVideo::getSceneGroupId, video.getSceneGroupId())
                .one();
        if (existed != null) {
            video.setId(existed.getId());
            videoService.updateById(video);
        } else {
            videoService.save(video);
        }

        videos.add(video);
    }

    private static class GroupBatchItem {
        final Long groupId;
        final List<Storyboard> groupSbs;
        final String template;
        final String artStyle;
        final String visualStyle;
        final String aspectRatio;
        final Map<Long, StoryboardImage> imageByStoryboardId;
        final String assetImageUrls;
        final String audioFileUrls;

        GroupBatchItem(Long groupId, List<Storyboard> groupSbs, String template,
                       String artStyle, String visualStyle, String aspectRatio,
                       Map<Long, StoryboardImage> imageByStoryboardId,
                       String assetImageUrls, String audioFileUrls) {
            this.groupId = groupId;
            this.groupSbs = groupSbs;
            this.template = template;
            this.artStyle = artStyle;
            this.visualStyle = visualStyle;
            this.aspectRatio = aspectRatio;
            this.imageByStoryboardId = imageByStoryboardId;
            this.assetImageUrls = assetImageUrls;
            this.audioFileUrls = audioFileUrls;
        }
    }
}