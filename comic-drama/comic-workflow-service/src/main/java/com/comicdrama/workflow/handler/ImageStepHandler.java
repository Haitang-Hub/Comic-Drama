package com.comicdrama.workflow.handler;

import com.comicdrama.common.ai.AiInvokeRequest;
import com.comicdrama.common.ai.AiInvokeResponse;
import com.comicdrama.common.broadcast.MessageBroadcaster;
import com.comicdrama.common.exception.BizException;
import com.comicdrama.common.service.TaskPauseChecker;
import com.comicdrama.workflow.entity.AssetDesign;
import com.comicdrama.workflow.entity.AssetImage;
import com.comicdrama.workflow.entity.Storyboard;
import com.comicdrama.workflow.entity.StoryboardImage;
import com.comicdrama.workflow.entity.StorySummary;
import com.comicdrama.workflow.service.StoryboardImageService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * STORYBOARD_IMAGE 步骤处理器：分镜绘图（步骤5）。
 * 根据分镜脚本的画面描述，并结合资产图片（人物/场景/道具），叠加画风+风格，生成分镜画面。
 *
 * <p>Phase-5：支持测试优先+批量模式，先测试一条，成功后批量生成。</p>
 */
@Slf4j
@Component
public class ImageStepHandler extends AbstractStepHandler {

    private final StoryboardImageService imageService;
    private final ObjectMapper objectMapper;

    public ImageStepHandler(List<com.comicdrama.common.ai.AiModelInvoker> invokers,
                            AiModelConfigProvider modelConfigProvider,
                            PromptTemplateProvider promptTemplateProvider,
                            TaskProgressRecorder progressRecorder,
                            TaskFailureRecorder failureRecorder,
                            MessageBroadcaster broadcaster,
                            StepModelBindingResolver bindingResolver,
                            TokenUsageRecorder tokenUsageRecorder,
                            TaskPauseChecker pauseChecker,
                            StoryboardImageService imageService,
                            ObjectMapper objectMapper) {
        super(invokers, modelConfigProvider, promptTemplateProvider, progressRecorder, failureRecorder,
                broadcaster, bindingResolver, tokenUsageRecorder, pauseChecker);
        this.imageService = imageService;
        this.objectMapper = objectMapper;
    }

    @Override
    public StepEnum getStep() {
        return StepEnum.STORYBOARD_IMAGE;
    }

    @Override
    protected void preCheck(StepContext context) {
        List<Storyboard> storyboards = context.getArtifact(StepEnum.STORYBOARD);
        if (storyboards == null || storyboards.isEmpty()) {
            throw new BizException("前置步骤[STORYBOARD]产物缺失，无法生成分镜画面");
        }
    }

    @Override
    protected void doExecute(StepContext context) throws Exception {
        List<AssetImage> assetImages = context.getArtifact(StepEnum.ASSET_IMAGE);
        if (assetImages == null) {
            assetImages = new ArrayList<>();
        }
        List<Storyboard> storyboards = context.getArtifact(StepEnum.STORYBOARD);

        List<Storyboard> sortedStoryboards = storyboards.stream()
                .sorted((a, b) -> Integer.compare(a.getSeq(), b.getSeq()))
                .collect(Collectors.toList());

        log.info("[STORYBOARD_IMAGE] 开始生成分镜画面，storyboardCount={}, assetImageCount={}, taskId={}",
                sortedStoryboards.size(), assetImages.size(), context.getTaskId());

        context.putArtifact(StepEnum.STORYBOARD_IMAGE, new ArrayList<StoryboardImage>());

        doBatchExecute(context);

        List<StoryboardImage> images = context.getArtifact(StepEnum.STORYBOARD_IMAGE);
        if (images == null || images.isEmpty()) {
            throw new BizException("所有分镜画面生成失败，无法继续执行后续步骤");
        }

        reportProgress(context, 100, "分镜画面生成完成");
        log.info("[STORYBOARD_IMAGE] 分镜画面生成成功，totalCount={}, taskId={}", images.size(), context.getTaskId());
    }

    @Override
    @SuppressWarnings("unchecked")
    protected <T> List<T> getBatchItems(StepContext context) {
        List<Storyboard> storyboards = context.getArtifact(StepEnum.STORYBOARD);
        return (List<T>) storyboards.stream()
                .sorted((a, b) -> Integer.compare(a.getSeq(), b.getSeq()))
                .collect(Collectors.toList());
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T, R> R processBatchItem(T item, int index, StepContext context) throws Exception {
        Storyboard sb = (Storyboard) item;

        List<AssetImage> assetImages = context.getArtifact(StepEnum.ASSET_IMAGE);
        if (assetImages == null) {
            assetImages = new ArrayList<>();
        }

        Map<String, AssetImage> imageByAssetName = new HashMap<>();
        for (AssetImage ai : assetImages) {
            if (ai != null && StringUtils.hasText(ai.getAssetName()) && StringUtils.hasText(ai.getImageUrl())) {
                imageByAssetName.put(ai.getAssetName(), ai);
            }
        }

        // 按分镜的 scene/character/props（分号分隔，含版本标识）匹配资产图片
        MatchedAssets matched = resolveAssetImages(sb, imageByAssetName);
        String refImageUrl = matched.primaryImageUrl;
        String assetImagesText = matched.formattedText;

        String artStyle = context.getRequestDTO().getArtStyle();
        String visualStyle = context.getRequestDTO().getVisualStyle();

        ImageSizeResolver.ImageSize imageSize = ImageSizeResolver.resolve(
                context.getRequestDTO().getAspectRatio(),
                context.getRequestDTO().getResolution());

        // 使用 storyboard_image 模板构建提示词
        String template = loadPromptTemplate("storyboard_image");
        String prompt = fillTemplate(template,
                "visual_desc", sb.getVisualDesc() != null ? sb.getVisualDesc() : "",
                "asset_images", assetImagesText,
                "art_style", artStyle != null ? artStyle : "",
                "visual_style", visualStyle != null ? visualStyle : "");

        AiInvokeRequest.AiInvokeRequestBuilder reqBuilder = AiInvokeRequest.builder()
                .modelProvider(getStep().getModelProvider())
                .nodeKey("image_sb_" + sb.getSeq())
                .prompt(prompt)
                .extra(Map.of("size", imageSize.toSizeString()));

        if (StringUtils.hasText(refImageUrl)) {
            reqBuilder.referenceImageUrl(refImageUrl);
        }

        AiInvokeResponse response = invokeByModel(context, reqBuilder.build());

        if (!response.isSuccess()) {
            log.warn("[STORYBOARD_IMAGE] 分镜{}画面生成失败: {}", sb.getSeq(), response.getErrorMessage());
            return null;
        }

        StoryboardImage image = createStoryboardImage(response, context, sb, refImageUrl,
                matched, imageSize, prompt);

        log.info("[STORYBOARD_IMAGE] 分镜{}画面生成成功，imageUrl={}", sb.getSeq(), response.getResourceUrl());
        return (R) image;
    }

    @Override
    protected int resolveBatchStartIndex(StepContext context, int totalSize) {
        long existingCount = imageService.lambdaQuery()
                .eq(StoryboardImage::getTaskId, context.getTaskId())
                .count();
        return (int) Math.min(existingCount, totalSize);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T, R> void saveBatchResult(T item, R result, StepContext context) {
        if (result == null) return;

        List<StoryboardImage> images = context.getArtifact(StepEnum.STORYBOARD_IMAGE);
        if (images == null) {
            images = new ArrayList<>();
            context.putArtifact(StepEnum.STORYBOARD_IMAGE, images);
        }

        StoryboardImage image = (StoryboardImage) result;

        StoryboardImage existed = imageService.lambdaQuery()
                .eq(StoryboardImage::getTaskId, image.getTaskId())
                .eq(StoryboardImage::getStoryboardId, image.getStoryboardId())
                .one();
        if (existed != null) {
            image.setId(existed.getId());
            imageService.updateById(image);
        } else {
            imageService.save(image);
        }

        images.add(image);
    }

    /** 分镜匹配到的资产图片集合 */
    static class MatchedAssets {
        final List<AssetImage> sceneImages = new ArrayList<>();
        final List<AssetImage> characterImages = new ArrayList<>();
        final List<AssetImage> propImages = new ArrayList<>();
        String primaryImageUrl;
        String formattedText;
    }

    /** 分号分隔符（中英文分号） */
    private static final String SEMICOLON_SPLIT = "[;；]";

    /**
     * 根据分镜的 scene/character/props（分号分隔，含版本标识），匹配资产图片。
     * 生成 asset_images 文本（供模板使用）和 primaryImageUrl（供 referenceImageUrl 使用）。
     */
    private MatchedAssets resolveAssetImages(Storyboard sb, Map<String, AssetImage> imageByAssetName) {
        MatchedAssets result = new MatchedAssets();

        if (imageByAssetName.isEmpty()) {
            result.formattedText = "";
            return result;
        }

        StringBuilder text = new StringBuilder();

        // 匹配场景图片
        collectMatchedImages(sb.getScene(), imageByAssetName, result.sceneImages, "场景", text);
        // 匹配角色图片
        collectMatchedImages(sb.getCharacter(), imageByAssetName, result.characterImages, "角色", text);
        // 匹配道具图片
        collectMatchedImages(sb.getProps(), imageByAssetName, result.propImages, "道具", text);

        // 主参考图：优先场景，其次第一个角色
        if (!result.sceneImages.isEmpty()) {
            result.primaryImageUrl = result.sceneImages.get(0).getImageUrl();
        } else if (!result.characterImages.isEmpty()) {
            result.primaryImageUrl = result.characterImages.get(0).getImageUrl();
        }

        result.formattedText = text.toString().trim();
        return result;
    }

    private void collectMatchedImages(String rawNames, Map<String, AssetImage> imageByAssetName,
                                      List<AssetImage> target, String typeLabel, StringBuilder text) {
        if (!StringUtils.hasText(rawNames) || "无".equals(rawNames.trim())) {
            return;
        }
        for (String n : rawNames.split(SEMICOLON_SPLIT)) {
            String name = n.trim();
            if (name.isEmpty() || name.equals("无")) continue;
            AssetImage img = imageByAssetName.get(name);
            if (img != null && StringUtils.hasText(img.getImageUrl())) {
                target.add(img);
                text.append(typeLabel).append("[").append(name).append("]: ")
                    .append(img.getImageUrl()).append("\n");
            }
        }
    }

    private StoryboardImage createStoryboardImage(AiInvokeResponse response, StepContext context,
                                                   Storyboard sb, String refImageUrl,
                                                   MatchedAssets matched,
                                                   ImageSizeResolver.ImageSize imageSize,
                                                   String prompt) {
        StoryboardImage image = new StoryboardImage();
        image.setTaskId(context.getTaskId());
        image.setStoryboardId(sb.getId());
        image.setImageUrl(response.getResourceUrl());
        image.setThumbnailUrl(response.getResourceUrl());
        image.setPromptUsed(prompt);
        image.setStatus(1);
        image.setRegenerateCount(0);
        image.setWidth(imageSize.getWidth());
        image.setHeight(imageSize.getHeight());

        // 填充场景/角色/道具资产ID引用
        if (!matched.sceneImages.isEmpty()) {
            List<String> refs = new ArrayList<>();
            for (AssetImage ai : matched.sceneImages) {
                if (ai.getAssetId() != null) refs.add(String.valueOf(ai.getAssetId()));
            }
            if (!refs.isEmpty()) image.setSceneRefs(String.join(",", refs));
        }
        if (!matched.characterImages.isEmpty()) {
            List<String> refs = new ArrayList<>();
            for (AssetImage ai : matched.characterImages) {
                if (ai.getAssetId() != null) refs.add(String.valueOf(ai.getAssetId()));
            }
            if (!refs.isEmpty()) image.setCharacterRefs(String.join(",", refs));
        }
        if (!matched.propImages.isEmpty()) {
            List<String> refs = new ArrayList<>();
            for (AssetImage ai : matched.propImages) {
                if (ai.getAssetId() != null) refs.add(String.valueOf(ai.getAssetId()));
            }
            if (!refs.isEmpty()) image.setPropRefs(String.join(",", refs));
        }

        return image;
    }
}