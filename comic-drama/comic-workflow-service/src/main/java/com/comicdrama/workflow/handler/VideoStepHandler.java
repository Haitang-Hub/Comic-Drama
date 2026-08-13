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
                    groupAudioUrls.toString(),
                    assetImages != null ? assetImages : java.util.Collections.emptyList()
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

        // 检测当前绑定的模型协议，决定走哪条参数装配路径
        com.comicdrama.workflow.entity.AiModelConfig boundConfig = getBoundModelConfig();
        String protocol = boundConfig != null ? boundConfig.getProtocol() : null;
        boolean isAgnesVideo = "agnes-video".equals(protocol);

        AiInvokeRequest.AiInvokeRequestBuilder reqBuilder = AiInvokeRequest.builder()
                .modelProvider(getStep().getModelProvider())
                .nodeKey("video_g" + groupId);

        StoryboardImage firstImg = groupItem.imageByStoryboardId.get(groupSbs.get(0).getId());

        if (isAgnesVideo) {
            // ===== Agnes Video 精准参数路径：提示词与图片序列一一对应 =====
            buildAgnesRequest(reqBuilder, groupItem, groupSbs);
        } else {
            // ===== 通用路径：模板填充（兼容 Seedance 等其他视频模型） =====
            buildGenericRequest(reqBuilder, groupItem, groupSbs);
        }

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

    /**
     * Agnes Video 参数装配：使用数据库提示词模板填充 prompt + 分镜图片通过 image_list 传给 Agnes。
     *
     * <p>设计原则（用户要求）：
     * <ul>
     *   <li>prompt 按照数据库模板（comic_drama.sql 第884-886行）填充，与通用路径一致</li>
     *   <li>分镜图片通过 extra.image_list 传给 Agnes（Invoker 自动转 Base64 data URI）</li>
     *   <li>逐帧提示词通过 extra.prompt_list 传给 Agnes（与图片一一对应）</li>
     *   <li>Agnes 特有参数：width/height/negative_prompt/duration/fps</li>
     * </ul>
     *
     * <p>关键帧数量分支（Agnes keyframes 模式要求 2~3 张图片）：
     * <ul>
     *   <li>N=0 → 纯文生，不传 image_list</li>
     *   <li>N=1 → 单图首帧锁定，走 referenceImageUrl 路径（Invoker 处理，省略 mode）</li>
     *   <li>N ∈ {2,3} → 正常 keyframes 模式</li>
     *   <li>N > 3 → 抽样首/中/尾3张</li>
     * </ul>
     */
    private void buildAgnesRequest(AiInvokeRequest.AiInvokeRequestBuilder reqBuilder,
                                   GroupBatchItem groupItem, List<Storyboard> groupSbs) {
        String artStyle = StringUtils.hasText(groupItem.artStyle) ? groupItem.artStyle : "真人";
        String visualStyle = StringUtils.hasText(groupItem.visualStyle) ? groupItem.visualStyle : "";
        String aspectRatio = StringUtils.hasText(groupItem.aspectRatio) ? groupItem.aspectRatio : "16:9";

        // 1. 按数据库模板填充 prompt（与通用路径一致，含 duration 变量）
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
        if (groupImageUrls.length() > 0) {
            groupImageUrls.setLength(groupImageUrls.length() - 1);
        }

        int totalDuration = groupSbs.stream()
                .mapToInt(sb -> sb.getDuration() == null ? 2 : sb.getDuration())
                .sum();

        String filledPrompt = fillTemplate(groupItem.template,
                "storyboards", groupText.toString(),
                "storyboard_image", groupImageUrls.toString(),
                "asset_images", groupItem.assetImageUrls,
                "audio_files", groupItem.audioFileUrls,
                "art_style", artStyle,
                "visual_style", visualStyle,
                "aspect_ratio", aspectRatio,
                "duration", String.valueOf(totalDuration));

        reqBuilder.prompt(filledPrompt);

        // 2. 收集所有分镜的图片+逐帧提示词（与分镜一一对应，有图的才纳入）
        List<String> allKeyframeUrls = new ArrayList<>();
        List<String> allFramePrompts = new ArrayList<>();
        for (Storyboard sb : groupSbs) {
            StoryboardImage img = groupItem.imageByStoryboardId.get(sb.getId());
            if (img != null && StringUtils.hasText(img.getImageUrl())) {
                allKeyframeUrls.add(img.getImageUrl());
                // 逐帧提示词：优先 visualDesc（画面描述），兜底 storyboardDesc，最后 shotDesc
                String framePrompt = StringUtils.hasText(sb.getVisualDesc())
                        ? sb.getVisualDesc() : sb.getStoryboardDesc();
                if (!StringUtils.hasText(framePrompt)) {
                    framePrompt = sb.getShotDesc();
                }
                allFramePrompts.add(framePrompt);
            }
        }

        // 3. 关键帧数量分支处理
        List<String> keyframeUrls = new ArrayList<>();
        List<String> framePrompts = new ArrayList<>();
        int n = allKeyframeUrls.size();
        if (n == 0) {
            log.info("[VIDEO] Agnes场景{}无分镜图片，走纯文生视频路径", groupItem.groupId);
        } else if (n == 1) {
            // 单图首帧锁定：不写入 extra.image_list，走外层 referenceImageUrl → Invoker 处理（省略 mode）
            log.info("[VIDEO] Agnes场景{}只有1张分镜图，走单图首帧锁定路径（非 keyframes 模式）", groupItem.groupId);
        } else if (n <= 3) {
            keyframeUrls.addAll(allKeyframeUrls);
            framePrompts.addAll(allFramePrompts);
            log.info("[VIDEO] Agnes场景{}有{}张分镜图，直接走 keyframes 模式", groupItem.groupId, n);
        } else {
            // >3 张 → 抽样：首帧 + 中间帧 + 尾帧
            int mid = n / 2;
            int[] sampleIndexes = {0, mid, n - 1};
            for (int idx : sampleIndexes) {
                keyframeUrls.add(allKeyframeUrls.get(idx));
                framePrompts.add(allFramePrompts.get(idx));
            }
            log.info("[VIDEO] Agnes场景{}分镜图{}张超过限制，抽样为首/中/尾3张走 keyframes 模式：indexes={}",
                    groupItem.groupId, n, java.util.Arrays.toString(sampleIndexes));
        }

        // 4. 构造 extra 参数（Agnes 特有）
        Map<String, Object> extra = new HashMap<>();
        // 仅 2~3 张时才传 image_list / prompt_list（对应 keyframes 模式）
        if (!keyframeUrls.isEmpty() && keyframeUrls.size() >= 2) {
            extra.put("image_list", keyframeUrls);
        }
        if (!framePrompts.isEmpty() && framePrompts.size() >= 2) {
            extra.put("prompt_list", framePrompts);
        }

        // 比例换算
        int[] dims = resolveDimensions(aspectRatio);
        extra.put("width", dims[0]);
        extra.put("height", dims[1]);

        // 反向提示词（对齐 gen_video.py NEG_PROMPT）
        extra.put("negative_prompt",
                "pc game, console game, video game, cartoon, childish, ugly, worst quality, "
                + "blurry, jittery, distorted, inconsistent appearance, face change, hair change, "
                + "clothes change, flicker, body deformation, scene mutation");

        // 时长与帧率
        extra.put("duration", Math.max(totalDuration, 5));
        extra.put("fps", 24);

        reqBuilder.extra(extra);

        log.info("[VIDEO] Agnes参数：groupId={}, keyframes={}, prompts={}, dims={}x{}, duration={}s, fps=24",
                groupItem.groupId, keyframeUrls.size(), framePrompts.size(),
                dims[0], dims[1], extra.get("duration"));
        if (!framePrompts.isEmpty()) {
            for (int i = 0; i < framePrompts.size(); i++) {
                log.info("[VIDEO] 逐帧提示词[{}]：{}", i,
                        framePrompts.get(i).length() > 80
                                ? framePrompts.get(i).substring(0, 80) + "..." : framePrompts.get(i));
            }
        }
    }

    /**
     * 根据视频比例换算具体的 width×height（Agnes 要求 8 的倍数）。
     */
    private int[] resolveDimensions(String aspectRatio) {
        if (aspectRatio == null || aspectRatio.isEmpty()) {
            return new int[]{1280, 720}; // 默认 16:9
        }
        switch (aspectRatio.trim()) {
            case "16:9": return new int[]{1280, 720};
            case "9:16": return new int[]{720, 1280};
            case "1:1": return new int[]{1024, 1024};
            case "4:3": return new int[]{1024, 768};
            case "3:4": return new int[]{768, 1024};
            default:     return new int[]{1280, 720};
        }
    }

    /**
     * 通用参数装配：模板填充路径（兼容 Seedance 等其他视频模型）。
     */
    private void buildGenericRequest(AiInvokeRequest.AiInvokeRequestBuilder reqBuilder,
                                     GroupBatchItem groupItem, List<Storyboard> groupSbs) {
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
        if (groupImageUrls.length() > 0) {
            groupImageUrls.setLength(groupImageUrls.length() - 1);
        }

        int totalDuration = groupSbs.stream()
                .mapToInt(sb -> sb.getDuration() == null ? 2 : sb.getDuration())
                .sum();

        String filledPrompt = fillTemplate(groupItem.template,
                "storyboards", groupText.toString(),
                "storyboard_image", groupImageUrls.toString(),
                "asset_images", groupItem.assetImageUrls,
                "audio_files", groupItem.audioFileUrls,
                "art_style", StringUtils.hasText(groupItem.artStyle) ? groupItem.artStyle : "",
                "visual_style", StringUtils.hasText(groupItem.visualStyle) ? groupItem.visualStyle : "",
                "aspect_ratio", StringUtils.hasText(groupItem.aspectRatio) ? groupItem.aspectRatio : "16:9",
                "duration", String.valueOf(totalDuration));

        reqBuilder.prompt(filledPrompt);
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
        /** 资产图片实体列表（含 assetName/assetType，供 Agnes 精准参数路径提取资产摘要） */
        final List<AssetImage> assetImages;

        GroupBatchItem(Long groupId, List<Storyboard> groupSbs, String template,
                       String artStyle, String visualStyle, String aspectRatio,
                       Map<Long, StoryboardImage> imageByStoryboardId,
                       String assetImageUrls, String audioFileUrls,
                       List<AssetImage> assetImages) {
            this.groupId = groupId;
            this.groupSbs = groupSbs;
            this.template = template;
            this.artStyle = artStyle;
            this.visualStyle = visualStyle;
            this.aspectRatio = aspectRatio;
            this.imageByStoryboardId = imageByStoryboardId;
            this.assetImageUrls = assetImageUrls;
            this.audioFileUrls = audioFileUrls;
            this.assetImages = assetImages;
        }
    }
}