package com.comicdrama.workflow.handler;

import com.comicdrama.common.ai.AiInvokeRequest;
import com.comicdrama.common.ai.AiInvokeResponse;
import com.comicdrama.common.broadcast.MessageBroadcaster;
import com.comicdrama.common.exception.BizException;
import com.comicdrama.common.service.TaskPauseChecker;
import com.comicdrama.common.storage.StorageService;
import com.comicdrama.workflow.entity.AssetImage;
import com.comicdrama.workflow.entity.SceneVideo;
import com.comicdrama.workflow.entity.Storyboard;
import com.comicdrama.workflow.entity.StoryboardAudio;
import com.comicdrama.workflow.entity.StoryboardImage;
import com.comicdrama.workflow.service.SceneVideoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * VIDEO 步骤处理器：视频生成（步骤8）。
 * 按场景分组执行，组内每个分镜各生成1条视频：
 * <ul>
 *   <li>组内第1个分镜 → 使用本分镜单张图生成视频（单图首帧锁定模式）</li>
 *   <li>组内第2+个分镜 → 使用「前一分镜图(首帧) + 本分镜图(尾帧)」双帧 keyframes 模式生成视频</li>
 * </ul>
 *
 * <p>Phase-5：支持测试优先+批量模式，先测试一条，成功后批量生成。</p>
 */
@Slf4j
@Component
public class VideoStepHandler extends AbstractStepHandler {

    private final SceneVideoService videoService;
    private final ObjectMapper objectMapper;
    private final StorageService storageService;

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
                            ObjectMapper objectMapper,
                            StorageService storageService) {
        super(invokers, modelConfigProvider, promptTemplateProvider, progressRecorder, failureRecorder,
                broadcaster, bindingResolver, tokenUsageRecorder, pauseChecker);
        this.videoService = videoService;
        this.objectMapper = objectMapper;
        this.storageService = storageService;
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

        reportProgress(context, 10, "正在加载 Prompt 模板并按分镜构建批量任务...");

        context.putArtifact(StepEnum.VIDEO, new ArrayList<SceneVideo>());

        int successCount = doBatchExecute(context);

        List<SceneVideo> videos = context.getArtifact(StepEnum.VIDEO);
        if (videos == null) {
            videos = new ArrayList<>();
        }

        log.info("[VIDEO] 分镜视频upsert完成，总数={}", videos.size());

        // 验证：如果有分镜但没有生成任何视频，抛出异常
        if (!storyboards.isEmpty() && videos.isEmpty()) {
            log.error("[VIDEO] 视频生成失败：有 {} 个分镜但未生成任何视频", storyboards.size());
            throw new BizException("[VIDEO] 视频生成失败：所有分镜视频均生成失败");
        }

        reportProgress(context, 100, "视频生成完成");
        log.info("[VIDEO] 视频生成成功，videoCount={}, taskId={}", videos.size(), context.getTaskId());
    }

    /**
     * 对外暴露的批量 item 构建方法（供单条视频重生成复用组内分镜链逻辑）。
     * 内部调用 protected getBatchItems。
     */
    @SuppressWarnings("rawtypes")
    public List getBatchItemsPublic(StepContext context) {
        return getBatchItems(context);
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

        // 先按 groupId 分组，保持原场景分组顺序
        Map<Long, List<Storyboard>> byGroup = sortedSbs.stream()
                .collect(Collectors.groupingBy(
                        sb -> sb.getGroupId() == null ? 0L : sb.getGroupId(),
                        LinkedHashMap::new,
                        Collectors.toList()));

        // 按分镜逐个展开为 item，附带组内上下文（isFirstInGroup、前一帧分镜图URL）
        List<StoryboardBatchItem> sbItems = new ArrayList<>();
        for (Map.Entry<Long, List<Storyboard>> entry : byGroup.entrySet()) {
            Long groupId = entry.getKey();
            List<Storyboard> groupSbs = entry.getValue();

            // 收集当前分组的配音文件URL（整组信息保留，用于prompt模板填充）
            StringBuilder groupAudioUrls = new StringBuilder();
            for (Storyboard sb : groupSbs) {
                String audioUrl = audioByStoryboardId.get(sb.getId());
                if (audioUrl != null) {
                    groupAudioUrls.append(audioUrl).append(",");
                }
            }
            if (groupAudioUrls.length() > 0) {
                groupAudioUrls.setLength(groupAudioUrls.length() - 1);
            }

            String prevImageUrl = null;
            Long prevSbId = null;
            for (int i = 0; i < groupSbs.size(); i++) {
                Storyboard sb = groupSbs.get(i);
                boolean isFirstInGroup = (i == 0);

                StoryboardImage curImg = imageByStoryboardId.get(sb.getId());
                String curImageUrl = (curImg != null && StringUtils.hasText(curImg.getImageUrl()))
                        ? curImg.getImageUrl() : null;

                // 本分镜对应的单条配音URL
                String sbAudioUrl = audioByStoryboardId.get(sb.getId());

                sbItems.add(new StoryboardBatchItem(
                        groupId,
                        sb,
                        isFirstInGroup,
                        prevImageUrl,
                        prevSbId,
                        template,
                        artStyle,
                        visualStyle,
                        aspectRatio,
                        curImageUrl,
                        imageByStoryboardId,
                        assetImageUrls.toString(),
                        groupAudioUrls.toString(),
                        sbAudioUrl,
                        assetImages != null ? assetImages : java.util.Collections.emptyList()
                ));

                // 将本分镜图和ID作为下一分镜的前帧（即使当前无图也保持prev与已生成链一致）
                if (curImageUrl != null) {
                    prevImageUrl = curImageUrl;
                }
                prevSbId = sb.getId();
            }
        }

        log.info("[VIDEO] 按分镜构建batch完成：{} 个分镜 ({} 个场景组)", sbItems.size(), byGroup.size());
        return (List<T>) sbItems;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T, R> R processBatchItem(T item, int index, StepContext context) throws Exception {
        StoryboardBatchItem sbItem = (StoryboardBatchItem) item;
        Long groupId = sbItem.groupId;
        Storyboard sb = sbItem.sb;
        boolean isFirstInGroup = sbItem.isFirstInGroup;
        String prevImageUrl = sbItem.prevFrameImageUrl;
        String curImageUrl = sbItem.curFrameImageUrl;

        // 组内后续帧：从前一个分镜已生成的视频中提取尾帧，替代分镜图片作为首帧
        if (!isFirstInGroup && sbItem.prevStoryboardId != null) {
            String lastFrameUrl = extractLastFrameFromPrevVideo(
                    context.getTaskId(), groupId, sbItem.prevStoryboardId, sb.getSeq());
            if (lastFrameUrl != null) {
                prevImageUrl = lastFrameUrl;
                log.info("[VIDEO] 场景{}_分镜seq={}：已从前一个视频提取尾帧作为首帧", groupId, sb.getSeq());
            } else {
                log.warn("[VIDEO] 场景{}_分镜seq={}：尾帧提取失败，降级使用前分镜图片作为首帧", groupId, sb.getSeq());
            }
        }

        // 检测当前绑定的模型协议，决定走哪条参数装配路径
        com.comicdrama.workflow.entity.AiModelConfig boundConfig = getBoundModelConfig();
        String protocol = boundConfig != null ? boundConfig.getProtocol() : null;
        boolean isAgnesVideo = "agnes-video".equals(protocol);

        AiInvokeRequest.AiInvokeRequestBuilder reqBuilder = AiInvokeRequest.builder()
                .modelProvider(getStep().getModelProvider())
                .nodeKey("video_g" + groupId + "_s" + sb.getSeq());

        if (isAgnesVideo) {
            // ===== Agnes Video 精准参数路径 =====
            buildAgnesRequest(reqBuilder, sbItem, sb, isFirstInGroup, prevImageUrl, curImageUrl);
        } else {
            // ===== 通用路径：模板填充（兼容 Seedance 等其他视频模型） =====
            buildGenericRequest(reqBuilder, sbItem, sb);
        }

        // referenceImageUrl 的处理由 Agnes 分支内精准控制
        // 组内第1个分镜的单图模式、或通用路径需要首图作为 reference 时，在 buildXxx 中已设置 referenceImageUrl

        AiInvokeResponse response = invokeByModel(context, reqBuilder.build());

        if (!response.isSuccess()) {
            log.warn("[VIDEO] 场景{}_分镜seq={}视频生成失败：{}", groupId, sb.getSeq(), response.getErrorMessage());
            return null;
        }

        SceneVideo video = new SceneVideo();
        video.setTaskId(context.getTaskId());
        video.setSceneGroupId(groupId);
        video.setStoryboardSeqRange(sb.getSeq() + "-" + sb.getSeq());
        video.setStoryboardIds(String.valueOf(sb.getId()));
        video.setFrameCount(1);
        video.setBaseFrameUrl(curImageUrl);
        video.setVideoUrl(response.getResourceUrl());
        int sbDuration = sb.getDuration() == null ? 2 : sb.getDuration();
        video.setDuration(BigDecimal.valueOf(sbDuration));
        video.setStatus(1);

        log.info("[VIDEO] 场景{}_分镜seq={}[{}]视频生成成功，url={}",
                groupId, sb.getSeq(), isFirstInGroup ? "首帧" : "后续帧", response.getResourceUrl());
        return (R) video;
    }

    /**
     * Agnes Video 参数装配：按单个分镜生成视频。
     *
     * <ul>
     *   <li>组内首帧 isFirstInGroup=true：单张分镜图 → referenceImageUrl 模式（首帧锁定），省略 keyframes mode</li>
     *   <li>组内后续帧 isFirstInGroup=false：image_list=[prevFrameImg, curFrameImg] + prompt_list=[逐帧描述] 双帧 keyframes 模式</li>
     * </ul>
     */
    private void buildAgnesRequest(AiInvokeRequest.AiInvokeRequestBuilder reqBuilder,
                                   StoryboardBatchItem sbItem,
                                   Storyboard sb,
                                   boolean isFirstInGroup,
                                   String prevImageUrl,
                                   String curImageUrl) {
        String artStyle = StringUtils.hasText(sbItem.artStyle) ? sbItem.artStyle : "真人";
        String visualStyle = StringUtils.hasText(sbItem.visualStyle) ? sbItem.visualStyle : "";
        String aspectRatio = StringUtils.hasText(sbItem.aspectRatio) ? sbItem.aspectRatio : "16:9";

        // 1. 按数据库模板填充 prompt（以本分镜为主，同时携带分组的资产/配音上下文）
        String sbText = String.format("[%d.%d] %s - %s",
                sb.getSeq(), sb.getLocalSeq() == null ? 0 : sb.getLocalSeq(),
                sb.getShotDesc(), sb.getVisualDesc());

        int totalDuration = Math.max(sb.getDuration() == null ? 2 : sb.getDuration(), 5);

        String filledPrompt = fillTemplate(sbItem.template,
                "storyboards", sbText,
                "storyboard_image", curImageUrl == null ? "" : curImageUrl,
                "asset_images", sbItem.assetImageUrls,
                "audio_files", sbItem.sbAudioFileUrl != null ? sbItem.sbAudioFileUrl : sbItem.groupAudioFileUrls,
                "art_style", artStyle,
                "visual_style", visualStyle,
                "aspect_ratio", aspectRatio,
                "duration", String.valueOf(totalDuration));

        reqBuilder.prompt(filledPrompt);

        // 2. 逐帧提示词：优先 visualDesc（画面描述），兜底 storyboardDesc，最后 shotDesc
        String framePrompt = StringUtils.hasText(sb.getVisualDesc())
                ? sb.getVisualDesc() : sb.getStoryboardDesc();
        if (!StringUtils.hasText(framePrompt)) {
            framePrompt = sb.getShotDesc();
        }

        // 3. 首帧 vs 后续帧：不同的模式
        Map<String, Object> extra = new HashMap<>();
        List<String> keyframeUrls = new ArrayList<>();
        List<String> framePrompts = new ArrayList<>();

        if (isFirstInGroup) {
            // === 组内首帧：单图首帧锁定模式 ===
            if (StringUtils.hasText(curImageUrl)) {
                // 走外层 referenceImageUrl → Invoker 处理（省略 keyframes mode）
                reqBuilder.referenceImageUrl(curImageUrl);
                log.info("[VIDEO] Agnes场景{}_分镜seq={}：组内首帧，走单图首帧锁定(非keyframes)，img={}",
                        sbItem.groupId, sb.getSeq(),
                        curImageUrl.length() > 60 ? curImageUrl.substring(0, 60) + "..." : curImageUrl);
            } else {
                log.info("[VIDEO] Agnes场景{}_分镜seq={}：组内首帧但无分镜图，走纯文生视频路径", sbItem.groupId, sb.getSeq());
            }
        } else {
            // === 组内后续帧：双帧 keyframes 模式 ===
            boolean hasPrev = StringUtils.hasText(prevImageUrl);
            boolean hasCur = StringUtils.hasText(curImageUrl);
            if (hasPrev && hasCur) {
                // 正常双帧：prev 首帧 + cur 尾帧
                keyframeUrls.add(prevImageUrl);
                keyframeUrls.add(curImageUrl);
                // prev 的 prompt 复用本分镜 prompt（或前帧的画面描述更合适，但上下文拿不到前帧的visual desc → 为保持稳定也用本分镜的prompt，Agnes会插值）
                framePrompts.add(framePrompt);
                framePrompts.add(framePrompt);
                log.info("[VIDEO] Agnes场景{}_分镜seq={}：组内后续帧，走双帧keyframes模式，prevImg={}, curImg={}",
                        sbItem.groupId, sb.getSeq(),
                        prevImageUrl.length() > 40 ? prevImageUrl.substring(0, 40) + "..." : prevImageUrl,
                        curImageUrl.length() > 40 ? curImageUrl.substring(0, 40) + "..." : curImageUrl);
            } else if (hasCur) {
                // 退化：前一帧图缺失，退化为单图 referenceImageUrl 模式
                reqBuilder.referenceImageUrl(curImageUrl);
                log.warn("[VIDEO] Agnes场景{}_分镜seq={}：组内后续帧但前帧图缺失，退化为单图首帧锁定模式",
                        sbItem.groupId, sb.getSeq());
            } else {
                log.warn("[VIDEO] Agnes场景{}_分镜seq={}：组内后续帧且无任何分镜图，走纯文生", sbItem.groupId, sb.getSeq());
            }
        }

        // 4. 构造 extra 参数（Agnes 特有）
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

        // 反向提示词
        extra.put("negative_prompt",
                "pc game, console game, video game, cartoon, childish, ugly, worst quality, "
                + "blurry, jittery, distorted, inconsistent appearance, face change, hair change, "
                + "clothes change, flicker, body deformation, scene mutation");

        // 时长与帧率
        extra.put("duration", totalDuration);
        extra.put("fps", 24);

        reqBuilder.extra(extra);

        log.info("[VIDEO] Agnes参数：groupId={}, seq={}, isFirstInGroup={}, keyframes={}, prompts={}, dims={}x{}, duration={}s, fps=24",
                sbItem.groupId, sb.getSeq(), isFirstInGroup,
                keyframeUrls.size(), framePrompts.size(),
                dims[0], dims[1], extra.get("duration"));
    }

    /**
     * 根据视频比例换算具体的 width×height（Agnes 要求 8 的倍数）。
     */
    private int[] resolveDimensions(String aspectRatio) {
        if (aspectRatio == null || aspectRatio.isEmpty()) {
            return new int[]{1280, 720};
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
                                     StoryboardBatchItem sbItem,
                                     Storyboard sb) {
        String sbText = String.format("[%d.%d] %s - %s",
                sb.getSeq(), sb.getLocalSeq() == null ? 0 : sb.getLocalSeq(),
                sb.getShotDesc(), sb.getVisualDesc());

        int sbDuration = sb.getDuration() == null ? 2 : sb.getDuration();

        String filledPrompt = fillTemplate(sbItem.template,
                "storyboards", sbText,
                "storyboard_image", sbItem.curFrameImageUrl != null ? sbItem.curFrameImageUrl : "",
                "asset_images", sbItem.assetImageUrls,
                "audio_files", sbItem.sbAudioFileUrl != null ? sbItem.sbAudioFileUrl : sbItem.groupAudioFileUrls,
                "art_style", StringUtils.hasText(sbItem.artStyle) ? sbItem.artStyle : "",
                "visual_style", StringUtils.hasText(sbItem.visualStyle) ? sbItem.visualStyle : "",
                "aspect_ratio", StringUtils.hasText(sbItem.aspectRatio) ? sbItem.aspectRatio : "16:9",
                "duration", String.valueOf(sbDuration));

        reqBuilder.prompt(filledPrompt);

        if (StringUtils.hasText(sbItem.curFrameImageUrl)) {
            reqBuilder.referenceImageUrl(sbItem.curFrameImageUrl);
        }
    }

    // ==================== 视频尾帧提取（ffmpeg） ====================

    /**
     * 查询前一个分镜的已生成视频，提取其尾帧并返回可访问的图片URL。
     * <p>组内后续分镜使用前一个视频的尾帧作为首帧，保证画面连贯性。</p>
     *
     * @param taskId           任务ID
     * @param groupId          场景组ID
     * @param prevStoryboardId 前一个分镜的ID
     * @param curSeq           当前分镜序号（用于日志和命名）
     * @return 尾帧图片URL，提取失败返回 null
     */
    private String extractLastFrameFromPrevVideo(Long taskId, Long groupId, Long prevStoryboardId, Integer curSeq) {
        try {
            // 1. 从DB查询前一个分镜的 SceneVideo
            SceneVideo prevVideo = videoService.lambdaQuery()
                    .eq(SceneVideo::getTaskId, taskId)
                    .eq(SceneVideo::getSceneGroupId, groupId)
                    .eq(SceneVideo::getStoryboardIds, String.valueOf(prevStoryboardId))
                    .one();

            if (prevVideo == null || !StringUtils.hasText(prevVideo.getVideoUrl())) {
                log.warn("[VIDEO] 未找到前分镜视频，taskId={}, groupId={}, prevSbId={}", taskId, groupId, prevStoryboardId);
                return null;
            }

            String videoUrl = prevVideo.getVideoUrl();
            String nodeKey = "g" + groupId + "_s" + curSeq;

            // 2. 提取尾帧
            return extractLastFrameFromVideo(videoUrl, taskId, nodeKey);

        } catch (Exception e) {
            log.warn("[VIDEO] 查询/提取前分镜视频尾帧异常：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 使用 ffmpeg 从视频文件中提取最后一帧，上传到存储服务并返回URL。
     * <p>ffmpeg 编译时 --disable-network，需先将视频下载到本地临时文件。</p>
     *
     * @param videoUrl 视频URL（本地存储的 http://127.0.0.1:8105/static/xxx.mp4）
     * @param taskId   任务ID（用于存储路径）
     * @param nodeKey  节点标识（用于文件命名）
     * @return 尾帧图片URL，失败返回 null
     */
    private String extractLastFrameFromVideo(String videoUrl, Long taskId, String nodeKey) {
        Path tempVideo = null;
        Path tempFrame = null;
        try {
            // 1. 下载视频到临时文件
            tempVideo = Files.createTempFile("video-prev-", ".mp4");
            try (InputStream is = URI.create(videoUrl).toURL().openStream()) {
                Files.copy(is, tempVideo, StandardCopyOption.REPLACE_EXISTING);
            }

            // 2. ffmpeg 提取最后一帧：-sseof -1 从文件末尾前1秒开始，取第一帧
            tempFrame = Files.createTempFile("frame-last-", ".jpg");
            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg", "-y",
                    "-sseof", "-1",
                    "-i", tempVideo.toString(),
                    "-frames:v", "1",
                    "-q:v", "2",
                    "-update", "1",
                    tempFrame.toString()
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String ffmpegOutput = new String(process.getInputStream().readAllBytes());
            int exitCode = process.waitFor();

            if (exitCode != 0 || !Files.exists(tempFrame) || Files.size(tempFrame) == 0) {
                log.warn("[VIDEO] ffmpeg提取尾帧失败，exitCode={}, output={}",
                        exitCode, ffmpegOutput.length() > 500 ? ffmpegOutput.substring(0, 500) : ffmpegOutput);
                return null;
            }

            // 3. 上传尾帧到存储服务
            String objectKey = taskId + "/video/lastframe_" + nodeKey + "_" + System.currentTimeMillis() + ".jpg";
            try (InputStream is = Files.newInputStream(tempFrame)) {
                String storedKey = storageService.upload(is, objectKey, -1, "image/jpeg");
                String frameUrl = storageService.signUrl(storedKey, 3600);
                log.info("[VIDEO] 尾帧提取成功，videoUrl={}, frameUrl={}",
                        videoUrl.length() > 60 ? videoUrl.substring(0, 60) + "..." : videoUrl,
                        frameUrl);
                return frameUrl;
            }

        } catch (Exception e) {
            log.warn("[VIDEO] 提取视频尾帧异常：videoUrl={}, error={}",
                    videoUrl.length() > 60 ? videoUrl.substring(0, 60) + "..." : videoUrl,
                    e.getMessage());
            return null;
        } finally {
            try { if (tempVideo != null) Files.deleteIfExists(tempVideo); } catch (Exception ignored) {}
            try { if (tempFrame != null) Files.deleteIfExists(tempFrame); } catch (Exception ignored) {}
        }
    }

    @Override
    protected int resolveBatchStartIndex(StepContext context, int totalSize) {
        // 改为按分镜粒度统计：每个分镜对应1条 SceneVideo
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
        StoryboardBatchItem sbItem = (StoryboardBatchItem) item;

        // upsert 唯一性：按 (taskId, sceneGroupId, storyboardSeqRange) 确定一个分镜
        SceneVideo existed = videoService.lambdaQuery()
                .eq(SceneVideo::getTaskId, video.getTaskId())
                .eq(SceneVideo::getSceneGroupId, video.getSceneGroupId())
                .eq(SceneVideo::getStoryboardSeqRange, video.getStoryboardSeqRange())
                .one();
        if (existed != null) {
            video.setId(existed.getId());
            videoService.updateById(video);
        } else {
            videoService.save(video);
        }

        videos.add(video);
    }

    /**
     * 单个分镜的批处理 item，携带组内上下文用于视频模式（首帧/后续帧）判断。
     * 提升为 public static 便于单条视频重生成时按字段匹配目标 item。
     */
    public static class StoryboardBatchItem {
        public final Long groupId;
        public final Storyboard sb;
        public final boolean isFirstInGroup;
        public final String prevFrameImageUrl;
        public final Long prevStoryboardId;
        final String template;
        final String artStyle;
        final String visualStyle;
        final String aspectRatio;
        public final String curFrameImageUrl;
        final Map<Long, StoryboardImage> imageByStoryboardId;
        final String assetImageUrls;
        final String groupAudioFileUrls;
        final String sbAudioFileUrl;
        final List<AssetImage> assetImages;

        StoryboardBatchItem(Long groupId, Storyboard sb, boolean isFirstInGroup,
                            String prevFrameImageUrl, Long prevStoryboardId,
                            String template, String artStyle, String visualStyle, String aspectRatio,
                            String curFrameImageUrl,
                            Map<Long, StoryboardImage> imageByStoryboardId,
                            String assetImageUrls, String groupAudioFileUrls, String sbAudioFileUrl,
                            List<AssetImage> assetImages) {
            this.groupId = groupId;
            this.sb = sb;
            this.isFirstInGroup = isFirstInGroup;
            this.prevFrameImageUrl = prevFrameImageUrl;
            this.prevStoryboardId = prevStoryboardId;
            this.template = template;
            this.artStyle = artStyle;
            this.visualStyle = visualStyle;
            this.aspectRatio = aspectRatio;
            this.curFrameImageUrl = curFrameImageUrl;
            this.imageByStoryboardId = imageByStoryboardId;
            this.assetImageUrls = assetImageUrls;
            this.groupAudioFileUrls = groupAudioFileUrls;
            this.sbAudioFileUrl = sbAudioFileUrl;
            this.assetImages = assetImages;
        }
    }
}
