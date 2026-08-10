package com.comicdrama.workflow.handler;

import com.comicdrama.common.ai.AiInvokeRequest;
import com.comicdrama.common.ai.AiInvokeResponse;
import com.comicdrama.common.broadcast.MessageBroadcaster;
import com.comicdrama.common.exception.BizException;
import com.comicdrama.common.service.TaskPauseChecker;
import com.comicdrama.workflow.entity.AssetDesign;
import com.comicdrama.workflow.entity.AssetImage;
import com.comicdrama.workflow.entity.StorySummary;
import com.comicdrama.workflow.service.AssetImageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ASSET_DERIVE 步骤处理器：衍生绘图（步骤5，图生图）。
 * 基于步骤4（ASSET_IMAGE）生成的首版资产图片，对版本>1 的衍生资产进行图生图，
 * 通过 derived_from 字段定位上一版本资产图片作为 base_image，生成衍生版本图片。
 * 按版本升序处理（v2 先于 v3），确保高版本的 base_image 已由低版本生成。
 * 如果没有版本>1 的资产，本步骤直接跳过。
 *
 * <p>Phase-5：支持测试优先+批量模式，先测试一条，成功后批量生成。</p>
 */
@Slf4j
@Component
public class AssetDeriveStepHandler extends AbstractStepHandler {

    private final AssetImageService assetImageService;

    public AssetDeriveStepHandler(List<com.comicdrama.common.ai.AiModelInvoker> invokers,
                                  AiModelConfigProvider modelConfigProvider,
                                  PromptTemplateProvider promptTemplateProvider,
                                  TaskProgressRecorder progressRecorder,
                                  TaskFailureRecorder failureRecorder,
                                  MessageBroadcaster broadcaster,
                                  StepModelBindingResolver bindingResolver,
                                  TokenUsageRecorder tokenUsageRecorder,
                                  TaskPauseChecker pauseChecker,
                                  AssetImageService assetImageService) {
        super(invokers, modelConfigProvider, promptTemplateProvider, progressRecorder, failureRecorder,
                broadcaster, bindingResolver, tokenUsageRecorder, pauseChecker);
        this.assetImageService = assetImageService;
    }

    @Override
    public StepEnum getStep() {
        return StepEnum.ASSET_DERIVE;
    }

    @Override
    protected void preCheck(StepContext context) {
        List<AssetDesign> assets = context.getArtifact(StepEnum.ASSET_DESIGN);
        if (assets == null || assets.isEmpty()) {
            throw new BizException("前置步骤[ASSET_DESIGN]产物缺失，无法进行衍生绘图");
        }
        List<AssetImage> baseImages = context.getArtifact(StepEnum.ASSET_IMAGE);
        if (baseImages == null) {
            throw new BizException("前置步骤[ASSET_IMAGE]产物缺失，无法进行衍生绘图");
        }
    }

    @Override
    protected void doExecute(StepContext context) throws Exception {
        List<AssetDesign> allAssets = context.getArtifact(StepEnum.ASSET_DESIGN);
        List<AssetImage> baseImages = context.getArtifact(StepEnum.ASSET_IMAGE);

        List<AssetDesign> deriveAssets = allAssets.stream()
                .filter(a -> a != null && a.getAssetType() != null
                        && !a.getAssetType().contains("音色")
                        && !a.getAssetType().equalsIgnoreCase("voice"))
                .filter(a -> a.getVersion() != null && a.getVersion() > 1)
                .sorted((a, b) -> Integer.compare(
                        a.getVersion() != null ? a.getVersion() : 1,
                        b.getVersion() != null ? b.getVersion() : 1))
                .collect(Collectors.toList());

        log.info("[ASSET_DERIVE] 开始衍生绘图（图生图），资产总数={}, 衍生资产数(version>1)={}, taskId={}",
                allAssets.size(), deriveAssets.size(), context.getTaskId());

        if (deriveAssets.isEmpty()) {
            log.info("[ASSET_DERIVE] 没有需要衍生绘图的资产（无版本>1的资产），跳过本步骤");
            context.putArtifact(StepEnum.ASSET_DERIVE, baseImages != null ? baseImages : new ArrayList<>());
            reportProgress(context, 100, "无衍生版本资产，跳过衍生绘图");
            return;
        }

        List<AssetImage> allImages = new ArrayList<>(baseImages != null ? baseImages : new ArrayList<>());
        context.putArtifact(StepEnum.ASSET_DERIVE, allImages);
        context.putArtifact(StepEnum.ASSET_IMAGE, new ArrayList<>(allImages));

        doBatchExecute(context);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected <T> List<T> getBatchItems(StepContext context) {
        List<AssetDesign> allAssets = context.getArtifact(StepEnum.ASSET_DESIGN);
        return (List<T>) allAssets.stream()
                .filter(a -> a != null && a.getAssetType() != null
                        && !a.getAssetType().contains("音色")
                        && !a.getAssetType().equalsIgnoreCase("voice"))
                .filter(a -> a.getVersion() != null && a.getVersion() > 1)
                .sorted((a, b) -> Integer.compare(
                        a.getVersion() != null ? a.getVersion() : 1,
                        b.getVersion() != null ? b.getVersion() : 1))
                .collect(Collectors.toList());
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T, R> R processBatchItem(T item, int index, StepContext context) throws Exception {
        AssetDesign asset = (AssetDesign) item;

        List<AssetImage> currentImages = context.getArtifact(StepEnum.ASSET_IMAGE);

        // 构建 assetName → AssetImage 映射，用于通过 derivedFrom 查找上一版本图片
        Map<String, AssetImage> baseImageByName = new HashMap<>();
        for (AssetImage img : currentImages) {
            if (img != null && StringUtils.hasText(img.getAssetName())) {
                baseImageByName.put(img.getAssetName(), img);
            }
        }

        // 通过 derived_from 定位上一版本资产图片作为 base_image
        String derivedFrom = asset.getDerivedFrom();
        AssetImage base = null;
        if (StringUtils.hasText(derivedFrom) && !"无".equals(derivedFrom)) {
            base = baseImageByName.get(derivedFrom.trim());
        }
        if (base == null || !StringUtils.hasText(base.getImageUrl())) {
            log.warn("[ASSET_DERIVE] 资产{}未找到上一版本图片(derivedFrom={})，跳过衍生绘图",
                    asset.getAssetName(), derivedFrom);
            return null;
        }

        String referenceUrl = base.getImageUrl();

        String artStyle = context.getRequestDTO().getArtStyle();
        String visualStyle = context.getRequestDTO().getVisualStyle();

        ImageSizeResolver.ImageSize imageSize = ImageSizeResolver.resolve(
                context.getRequestDTO().getAspectRatio(),
                context.getRequestDTO().getResolution());

        // 使用 asset_derive 模板构建提示词
        String template = loadPromptTemplate("asset_derive");
        String prompt = fillTemplate(template,
                "base_image", referenceUrl,
                "asset_desc", asset.getAssetDesc() != null ? asset.getAssetDesc() : "",
                "art_style", artStyle != null ? artStyle : "",
                "visual_style", visualStyle != null ? visualStyle : "");

        AiInvokeRequest req = AiInvokeRequest.builder()
                .modelProvider(getStep().getModelProvider())
                .nodeKey("asset_derive_" + asset.getAssetType() + "_" + asset.getAssetName() + "_derive")
                .prompt(prompt)
                .referenceImageUrl(referenceUrl)
                .extra(Map.of("size", imageSize.toSizeString()))
                .build();

        AiInvokeResponse response = invokeByModel(context, req);

        if (!response.isSuccess()) {
            log.warn("[ASSET_DERIVE] 资产{}衍生绘图失败: {}", asset.getAssetName(), response.getErrorMessage());
            return null;
        }

        AssetImage img = new AssetImage();
        img.setTaskId(context.getTaskId());
        img.setAssetId(asset.getId());
        img.setAssetType(asset.getAssetType());
        img.setAssetName(asset.getAssetName());
        img.setImageUrl(response.getResourceUrl());
        img.setThumbnailUrl(response.getResourceUrl());
        img.setBaseImageId(base.getId());
        img.setBaseImageUrl(referenceUrl);
        img.setPromptUsed(prompt);
        img.setStatus(1);
        img.setWidth(imageSize.getWidth());
        img.setHeight(imageSize.getHeight());

        asset.setResourceUrl(response.getResourceUrl());

        log.info("[ASSET_DERIVE] 资产{}衍生绘图成功(version={}, derivedFrom={})，url={}",
                asset.getAssetName(), asset.getVersion(), derivedFrom, response.getResourceUrl());
        return (R) img;
    }

    /** 将单个资产格式化为模板可读的文本 */
    private String formatAssetForTemplate(AssetDesign asset) {
        StringBuilder sb = new StringBuilder();
        sb.append("资产类型：").append(asset.getAssetType()).append("\n");
        sb.append("资产名称：").append(asset.getAssetName()).append("\n");
        if (StringUtils.hasText(asset.getBaseAssetName())) {
            sb.append("基础资产名：").append(asset.getBaseAssetName()).append("\n");
        }
        sb.append("衍生自：").append(asset.getDerivedFrom() != null ? asset.getDerivedFrom() : "无").append("\n");
        sb.append("资产描述：").append(asset.getAssetDesc());
        return sb.toString();
    }

    @Override
    protected int resolveBatchStartIndex(StepContext context, int totalSize) {
        long existingCount = assetImageService.lambdaQuery()
                .eq(AssetImage::getTaskId, context.getTaskId())
                .isNotNull(AssetImage::getBaseImageId)
                .count();
        return (int) Math.min(existingCount, totalSize);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T, R> void saveBatchResult(T item, R result, StepContext context) {
        if (result == null) return;

        AssetImage img = (AssetImage) result;

        AssetImage existed = assetImageService.lambdaQuery()
                .eq(AssetImage::getTaskId, img.getTaskId())
                .eq(AssetImage::getAssetId, img.getAssetId())
                .eq(AssetImage::getBaseImageId, img.getBaseImageId())
                .one();
        if (existed != null) {
            img.setId(existed.getId());
            assetImageService.updateById(img);
        } else {
            assetImageService.save(img);
        }

        List<AssetImage> deriveImages = context.getArtifact(StepEnum.ASSET_DERIVE);
        if (deriveImages == null) {
            deriveImages = new ArrayList<>();
            context.putArtifact(StepEnum.ASSET_DERIVE, deriveImages);
        }
        deriveImages.add(img);

        List<AssetImage> assetImages = context.getArtifact(StepEnum.ASSET_IMAGE);
        if (assetImages == null) {
            assetImages = new ArrayList<>();
            context.putArtifact(StepEnum.ASSET_IMAGE, assetImages);
        }
        assetImages.add(img);
    }
}