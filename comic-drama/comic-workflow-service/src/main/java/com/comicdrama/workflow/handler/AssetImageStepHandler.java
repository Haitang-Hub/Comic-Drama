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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ASSET_IMAGE 步骤处理器：资产绘图（步骤4，文生图）。
 * 根据资产脚本（人物/场景/道具），结合画风+风格，生成资产图片。
 * 衍生处理由 ASSET_DERIVE 步骤（图生图）负责。
 * 注意：音色资产不需要生成图片。
 *
 * <p>Phase-5：支持测试优先+批量模式，先测试一条，成功后批量生成。</p>
 */
@Slf4j
@Component
public class AssetImageStepHandler extends AbstractStepHandler {

    private final AssetImageService assetImageService;

    public AssetImageStepHandler(List<com.comicdrama.common.ai.AiModelInvoker> invokers,
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
        return StepEnum.ASSET_IMAGE;
    }

    @Override
    protected void preCheck(StepContext context) {
        List<AssetDesign> assets = context.getArtifact(StepEnum.ASSET_DESIGN);
        if (assets == null || assets.isEmpty()) {
            throw new BizException("前置步骤[ASSET_DESIGN]产物缺失，无法进行资产绘图");
        }
    }

    @Override
    protected void doExecute(StepContext context) throws Exception {
        List<AssetDesign> allAssets = context.getArtifact(StepEnum.ASSET_DESIGN);

        List<AssetDesign> firstVersionAssets = allAssets.stream()
                .filter(a -> a != null && a.getAssetType() != null
                        && !a.getAssetType().contains("音色")
                        && !a.getAssetType().equalsIgnoreCase("voice"))
                .filter(a -> a.getVersion() == null || a.getVersion() <= 1)
                .collect(Collectors.toList());

        log.info("[ASSET_IMAGE] 开始资产绘图（文生图），资产总数={}, 首版可绘图资产数={}, taskId={}",
                allAssets.size(), firstVersionAssets.size(), context.getTaskId());

        context.putArtifact(StepEnum.ASSET_IMAGE, new ArrayList<>());

        if (firstVersionAssets.isEmpty()) {
            log.warn("[ASSET_IMAGE] 没有可绘图的首版资产，直接跳过本步骤");
            reportProgress(context, 100, "没有需要绘图的首版资产，跳过");
            return;
        }

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
                .filter(a -> a.getVersion() == null || a.getVersion() <= 1)
                .collect(Collectors.toList());
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T, R> R processBatchItem(T item, int index, StepContext context) throws Exception {
        AssetDesign asset = (AssetDesign) item;

        String artStyle = context.getRequestDTO().getArtStyle();
        String visualStyle = context.getRequestDTO().getVisualStyle();

        ImageSizeResolver.ImageSize imageSize = ImageSizeResolver.resolve(
                context.getRequestDTO().getAspectRatio(),
                context.getRequestDTO().getResolution());

        String template = loadPromptTemplate("asset_image");
        String prompt = fillTemplate(template,
                "asset_desc", asset.getAssetDesc() != null ? asset.getAssetDesc() : "",
                "art_style", artStyle != null ? artStyle : "",
                "visual_style", visualStyle != null ? visualStyle : "");

        AiInvokeRequest req = AiInvokeRequest.builder()
                .modelProvider(getStep().getModelProvider())
                .nodeKey("asset_img_" + asset.getAssetType() + "_" +
                        (asset.getAssetName() == null ? index : asset.getAssetName().replaceAll("[^\\w\\u4e00-\\u9fa5]", "")))
                .prompt(prompt)
                .extra(Map.of("size", imageSize.toSizeString()))
                .build();

        AiInvokeResponse response = invokeByModel(context, req);
        if (!response.isSuccess()) {
            log.warn("[ASSET_IMAGE] 资产{}绘图失败: {}", asset.getAssetName(), response.getErrorMessage());
            return null;
        }

        AssetImage img = new AssetImage();
        img.setTaskId(context.getTaskId());
        img.setAssetId(asset.getId());
        img.setAssetType(asset.getAssetType());
        img.setAssetName(asset.getAssetName());
        img.setImageUrl(response.getResourceUrl());
        img.setThumbnailUrl(response.getResourceUrl());
        img.setPromptUsed(prompt);
        img.setStatus(1);
        img.setWidth(imageSize.getWidth());
        img.setHeight(imageSize.getHeight());

        asset.setResourceUrl(response.getResourceUrl());

        log.info("[ASSET_IMAGE] 资产{}绘图成功，url={}", asset.getAssetName(), response.getResourceUrl());
        return (R) img;
    }

    @Override
    protected int resolveBatchStartIndex(StepContext context, int totalSize) {
        long existingCount = assetImageService.lambdaQuery()
                .eq(AssetImage::getTaskId, context.getTaskId())
                .isNull(AssetImage::getBaseImageId) // 仅统计步骤4的首版资产图，排除步骤5的衍生图（有base_image_id）
                .count();
        return (int) Math.min(existingCount, totalSize);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T, R> void saveBatchResult(T item, R result, StepContext context) {
        if (result == null) return;

        List<AssetImage> images = context.getArtifact(StepEnum.ASSET_IMAGE);
        if (images == null) {
            images = new ArrayList<>();
            context.putArtifact(StepEnum.ASSET_IMAGE, images);
        }

        AssetImage img = (AssetImage) result;

        AssetImage existed = assetImageService.lambdaQuery()
                .eq(AssetImage::getTaskId, img.getTaskId())
                .eq(AssetImage::getAssetId, img.getAssetId())
                .one();
        if (existed != null) {
            img.setId(existed.getId());
            assetImageService.updateById(img);
        } else {
            assetImageService.save(img);
        }

        images.add(img);
    }

    /** 将单个资产格式化为模板可读的文本 */
    private String formatAssetForTemplate(AssetDesign asset) {
        StringBuilder sb = new StringBuilder();
        sb.append("资产类型：").append(asset.getAssetType()).append("\n");
        sb.append("资产名称：").append(asset.getAssetName()).append("\n");
        if (StringUtils.hasText(asset.getBaseAssetName())) {
            sb.append("基础资产名：").append(asset.getBaseAssetName()).append("\n");
        }
        sb.append("资产描述：").append(asset.getAssetDesc());
        return sb.toString();
    }
}
