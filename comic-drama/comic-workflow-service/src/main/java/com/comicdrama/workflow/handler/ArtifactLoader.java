package com.comicdrama.workflow.handler;

import com.comicdrama.workflow.entity.AssetDesign;
import com.comicdrama.workflow.entity.AssetImage;
import com.comicdrama.workflow.entity.SceneVideo;
import com.comicdrama.workflow.entity.StorySummary;
import com.comicdrama.workflow.entity.Storyboard;
import com.comicdrama.workflow.entity.StoryboardAudio;
import com.comicdrama.workflow.entity.StoryboardImage;
import com.comicdrama.workflow.service.AssetDesignService;
import com.comicdrama.workflow.service.AssetImageService;
import com.comicdrama.workflow.service.SceneVideoService;
import com.comicdrama.workflow.service.StorySummaryService;
import com.comicdrama.workflow.service.StoryboardAudioService;
import com.comicdrama.workflow.service.StoryboardImageService;
import com.comicdrama.workflow.service.StoryboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 产物加载器：断点续跑/重试时从数据库恢复已完成步骤的产物到 {@link StepContext}。
 *
 * <p>当流水线从中间步骤（startStep &gt; 1）继续执行时，前置步骤会被跳过，
 * 但它们的产物存在于数据库中。本组件负责将这些产物重新加载到 context，
 * 供后续步骤的 {@code preCheck} 和 {@code doExecute} 使用。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ArtifactLoader {

    private final StorySummaryService storySummaryService;
    private final StoryboardService storyboardService;
    private final AssetDesignService assetDesignService;
    private final AssetImageService assetImageService;
    private final StoryboardImageService storyboardImageService;
    private final StoryboardAudioService storyboardAudioService;
    private final SceneVideoService sceneVideoService;

    /**
     * 从数据库加载已完成步骤的产物到 context。
     * 仅加载 startStep 之前（不含）的步骤产物。
     *
     * @param context   步骤上下文
     * @param taskId    任务 ID
     * @param startStep 起始步骤编号（从该步骤开始执行，之前的产物需要恢复）
     */
    public void loadArtifacts(StepContext context, Long taskId, int startStep) {
        log.info("开始从数据库恢复产物 taskId={}, startStep={}", taskId, startStep);

        int restored = 0;

        // 步骤1：故事摘要（单个对象，直接取第一个）
        if (startStep > StepEnum.SUMMARY.getOrder()) {
            List<StorySummary> summaries = storySummaryService.listByTaskId(taskId);
            if (summaries != null && !summaries.isEmpty()) {
                StorySummary current = summaries.get(0);
                context.putArtifact(StepEnum.SUMMARY, current);
                restored++;
                log.info("已恢复步骤1产物 [SUMMARY], contentLen={}",
                        current.getContent() != null ? current.getContent().length() : 0);
            } else {
                log.warn("步骤1产物 [SUMMARY] 数据库中未找到，taskId={}", taskId);
            }
        }

        // 步骤2：分镜脚本（列表）
        if (startStep > StepEnum.STORYBOARD.getOrder()) {
            List<Storyboard> storyboards = storyboardService.listByTaskId(taskId);
            if (storyboards != null && !storyboards.isEmpty()) {
                context.putArtifact(StepEnum.STORYBOARD, storyboards);
                restored++;
                log.info("已恢复步骤2产物 [STORYBOARD], count={}", storyboards.size());
            } else {
                log.warn("步骤2产物 [STORYBOARD] 数据库中未找到，taskId={}", taskId);
            }
        }

        // 步骤3：资产设计（列表）
        if (startStep > StepEnum.ASSET_DESIGN.getOrder()) {
            List<AssetDesign> assets = assetDesignService.listByTaskId(taskId);
            if (assets != null && !assets.isEmpty()) {
                context.putArtifact(StepEnum.ASSET_DESIGN, assets);
                restored++;
                log.info("已恢复步骤3产物 [ASSET_DESIGN], count={}", assets.size());
            } else {
                log.warn("步骤3产物 [ASSET_DESIGN] 数据库中未找到，taskId={}", taskId);
            }
        }

        // 步骤4：资产图片（列表，可能为空——如全部为音色资产时步骤4会跳过绘图）
        if (startStep > StepEnum.ASSET_IMAGE.getOrder()) {
            List<AssetImage> images = assetImageService.listByTaskId(taskId);
            context.putArtifact(StepEnum.ASSET_IMAGE, images != null ? images : java.util.Collections.emptyList());
            restored++;
            log.info("已恢复步骤4产物 [ASSET_IMAGE], count={}", images != null ? images.size() : 0);
        }

        // 步骤5：衍生图片（图生图产物，与步骤4同表 asset_image）
        // 步骤5 的产物是完整 AssetImage 列表，直接加载全部并同步到 ASSET_IMAGE 产物
        if (startStep > StepEnum.ASSET_DERIVE.getOrder()) {
            List<AssetImage> allImages = assetImageService.listByTaskId(taskId);
            List<AssetImage> imageList = allImages != null ? allImages : java.util.Collections.emptyList();
            context.putArtifact(StepEnum.ASSET_DERIVE, imageList);
            // 同步更新 ASSET_IMAGE 产物，确保下游步骤（分镜绘图）能获取到完整列表
            context.putArtifact(StepEnum.ASSET_IMAGE, imageList);
            restored++;
            log.info("已恢复步骤5产物 [ASSET_DERIVE]（完整资产图片列表）, count={}", imageList.size());
        }

        // 步骤6：分镜画面（列表）
        if (startStep > StepEnum.STORYBOARD_IMAGE.getOrder()) {
            List<StoryboardImage> sbImages = storyboardImageService.listByTaskId(taskId);
            List<StoryboardImage> imageList = sbImages != null ? sbImages : java.util.Collections.emptyList();
            context.putArtifact(StepEnum.STORYBOARD_IMAGE, imageList);
            restored++;
            if (!imageList.isEmpty()) {
                log.info("已恢复步骤6产物 [STORYBOARD_IMAGE], count={}", imageList.size());
            } else {
                log.warn("步骤6产物 [STORYBOARD_IMAGE] 数据库中未找到或为空，taskId={}", taskId);
            }
        }

        // 步骤7：配音（列表，可能为空——如未启用配音步骤）
        if (startStep > StepEnum.AUDIO.getOrder()) {
            List<StoryboardAudio> audios = storyboardAudioService.listByTaskId(taskId);
            List<StoryboardAudio> audioList = audios != null ? audios : java.util.Collections.emptyList();
            context.putArtifact(StepEnum.AUDIO, audioList);
            restored++;
            log.info("已恢复步骤7产物 [AUDIO], count={}", audioList.size());
        }

        // 步骤8：场景视频（列表）
        if (startStep > StepEnum.VIDEO.getOrder()) {
            List<SceneVideo> videos = sceneVideoService.listByTaskId(taskId);
            List<SceneVideo> videoList = videos != null ? videos : java.util.Collections.emptyList();
            context.putArtifact(StepEnum.VIDEO, videoList);
            restored++;
            if (!videoList.isEmpty()) {
                log.info("已恢复步骤8产物 [VIDEO], count={}", videoList.size());
            } else {
                log.warn("步骤8产物 [VIDEO] 数据库中未找到或为空，taskId={}", taskId);
            }
        }

        log.info("产物恢复完成 taskId={}, 恢复了 {} 个步骤的产物", taskId, restored);
    }
}
