package com.comicdrama.workflow.controller;

import com.comicdrama.common.result.Result;
import com.comicdrama.common.service.TaskStateManager;
import com.comicdrama.workflow.dto.PipelineExecuteRequest;
import com.comicdrama.workflow.service.impl.DefaultTaskInfoProvider;
import com.comicdrama.workflow.service.impl.WorkflowPipelineServiceImpl;
import com.comicdrama.workflow.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

/**
 * 流水线执行控制器（供 task-service 远程调用触发流水线执行）。
 *
 * <p>负责接收来自 task-service 的请求，注册任务信息，
 * 并异步调用 {@link WorkflowPipelineServiceImpl} 执行 AI 流水线。
 * 立即返回"已接受"状态，避免 HTTP 超时。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/workflow/pipeline")
@RequiredArgsConstructor
public class PipelineController {

    private final WorkflowPipelineServiceImpl workflowPipelineService;
    private final DefaultTaskInfoProvider taskInfoProvider;
    private final StorySummaryService storySummaryService;
    private final StoryboardService storyboardService;
    private final AssetDesignService assetDesignService;
    private final AssetImageService assetImageService;
    private final StoryboardImageService storyboardImageService;
    private final StoryboardAudioService storyboardAudioService;
    private final SceneVideoService sceneVideoService;
    private final TaskStateManager taskStateManager;

    @Value("${comic.pipeline.async-threads:2}")
    private int asyncThreads;

    @Value("${comic.pipeline.queue-capacity:50}")
    private int queueCapacity;

    private ExecutorService asyncExecutor;

    @PostConstruct
    void initExecutor() {
        int threads = Math.max(1, asyncThreads);
        int queue = Math.max(1, queueCapacity);
        asyncExecutor = new ThreadPoolExecutor(
                threads, threads, 60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queue),
                r -> {
                    Thread t = new Thread(r, "pipeline-async");
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy()  // 队列满时由调用线程执行，起背压作用
        );
        log.info("PipelineController 异步线程池初始化：coreThreads={}, queueCapacity={}", threads, queue);
    }

    /**
     * 执行流水线（异步执行，立即返回）。
     */
    @PostMapping("/execute")
    public Result<Void> execute(@RequestBody PipelineExecuteRequest request) {
        if (request.getTaskId() == null) {
            return Result.fail("taskId 不能为空");
        }

        log.info("收到流水线执行请求 taskId={}, userId={}, maxSteps={}",
                request.getTaskId(), request.getUserId(), request.getMaxSteps());

        taskInfoProvider.registerTask(
                request.getTaskId(),
                request.getUserId() != null ? request.getUserId() : 1L,
                request.getTitle() != null ? request.getTitle() : "Untitled",
                request.getTaskCreateDTO() != null ? request.getTaskCreateDTO() : new com.comicdrama.common.dto.TaskCreateDTO());

        int maxSteps = request.getMaxSteps() != null ? request.getMaxSteps() : 9;
        Long taskId = request.getTaskId();

        // 异步执行流水线，立即返回成功
        CompletableFuture.runAsync(() -> {
            try {
                log.info("异步开始执行流水线 taskId={}, maxSteps={}", taskId, maxSteps);
                workflowPipelineService.executePipeline(taskId, maxSteps);
                log.info("流水线执行完成 taskId={}", taskId);
            } catch (Exception e) {
                log.error("流水线执行失败 taskId={}", taskId, e);
            }
        }, asyncExecutor);

        return Result.ok();
    }

    /**
     * 从指定步骤继续执行流水线（异步执行，立即返回）。
     */
    @PostMapping("/resume")
    public Result<Void> resume(@RequestBody PipelineExecuteRequest request,
                               @RequestParam int startStep) {
        if (request.getTaskId() == null) {
            return Result.fail("taskId 不能为空");
        }

        log.info("收到断点续跑请求 taskId={}, startStep={}", request.getTaskId(), startStep);

        taskInfoProvider.registerTask(
                request.getTaskId(),
                request.getUserId() != null ? request.getUserId() : 1L,
                request.getTitle() != null ? request.getTitle() : "Untitled",
                request.getTaskCreateDTO() != null ? request.getTaskCreateDTO() : new com.comicdrama.common.dto.TaskCreateDTO());

        int maxSteps = request.getMaxSteps() != null ? request.getMaxSteps() : 9;
        Long taskId = request.getTaskId();

        // 异步执行断点续跑，立即返回成功
        CompletableFuture.runAsync(() -> {
            try {
                log.info("异步开始断点续跑 taskId={}, startStep={}, maxSteps={}", taskId, startStep, maxSteps);
                workflowPipelineService.executeFromStep(taskId, startStep, maxSteps);
                log.info("断点续跑完成 taskId={}", taskId);
            } catch (Exception e) {
                log.error("断点续跑失败 taskId={}", taskId, e);
            }
        }, asyncExecutor);

        return Result.ok();
    }

    /**
     * 单步重新生成（异步执行，立即返回）。
     */
    @PostMapping("/regenerate")
    public Result<Void> regenerate(@RequestBody PipelineExecuteRequest request,
                                   @RequestParam int stepOrder) {
        if (request.getTaskId() == null) {
            return Result.fail("taskId 不能为空");
        }

        log.info("收到单步重生成请求 taskId={}, stepOrder={}, overrides={}", request.getTaskId(), stepOrder, request.getOverrides());

        taskInfoProvider.registerTask(
                request.getTaskId(),
                request.getUserId() != null ? request.getUserId() : 1L,
                request.getTitle() != null ? request.getTitle() : "Untitled",
                request.getTaskCreateDTO() != null ? request.getTaskCreateDTO() : new com.comicdrama.common.dto.TaskCreateDTO());

        Long taskId = request.getTaskId();
        Map<String, Object> overrides = request.getOverrides();

        // 异步执行单步重生成，立即返回成功
        CompletableFuture.runAsync(() -> {
            try {
                log.info("异步开始单步重生成 taskId={}, stepOrder={}", taskId, stepOrder);
                workflowPipelineService.regenerateNode(taskId, stepOrder, overrides);
                log.info("单步重生成完成 taskId={}", taskId);
            } catch (Exception e) {
                log.error("单步重生成失败 taskId={}", taskId, e);
            }
        }, asyncExecutor);

        return Result.ok();
    }

    /**
     * 单张资产图重生成（步骤4 首版 / 步骤5 衍生共用，按 imageId 自动判定）。
     * 异步执行，立即返回。
     */
    @PostMapping("/regenerate/asset-image")
    public Result<Void> regenerateAssetImage(@RequestBody PipelineExecuteRequest request,
                                             @RequestParam Long imageId) {
        if (request.getTaskId() == null) return Result.fail("taskId 不能为空");
        if (imageId == null) return Result.fail("imageId 不能为空");

        log.info("收到单张资产图重生成请求 taskId={}, imageId={}, overrides={}",
                request.getTaskId(), imageId, request.getOverrides());

        taskInfoProvider.registerTask(
                request.getTaskId(),
                request.getUserId() != null ? request.getUserId() : 1L,
                request.getTitle() != null ? request.getTitle() : "Untitled",
                request.getTaskCreateDTO() != null ? request.getTaskCreateDTO() : new com.comicdrama.common.dto.TaskCreateDTO());

        Long taskId = request.getTaskId();
        java.util.Map<String, Object> overrides = request.getOverrides();
        CompletableFuture.runAsync(() -> {
            try {
                log.info("异步开始单张资产图重生成 taskId={}, imageId={}", taskId, imageId);
                workflowPipelineService.regenerateAssetImage(taskId, imageId, overrides);
                log.info("单张资产图重生成完成 taskId={}, imageId={}", taskId, imageId);
            } catch (Exception e) {
                log.error("单张资产图重生成失败 taskId={}, imageId={}", taskId, imageId, e);
            }
        }, asyncExecutor);

        return Result.ok();
    }

    /**
     * 单张分镜图重生成（步骤6）。
     * 异步执行，立即返回。
     */
    @PostMapping("/regenerate/storyboard-image")
    public Result<Void> regenerateStoryboardImage(@RequestBody PipelineExecuteRequest request,
                                                  @RequestParam Long imageId) {
        if (request.getTaskId() == null) return Result.fail("taskId 不能为空");
        if (imageId == null) return Result.fail("imageId 不能为空");

        log.info("收到单张分镜图重生成请求 taskId={}, imageId={}, overrides={}",
                request.getTaskId(), imageId, request.getOverrides());

        taskInfoProvider.registerTask(
                request.getTaskId(),
                request.getUserId() != null ? request.getUserId() : 1L,
                request.getTitle() != null ? request.getTitle() : "Untitled",
                request.getTaskCreateDTO() != null ? request.getTaskCreateDTO() : new com.comicdrama.common.dto.TaskCreateDTO());

        Long taskId = request.getTaskId();
        java.util.Map<String, Object> overrides = request.getOverrides();
        CompletableFuture.runAsync(() -> {
            try {
                log.info("异步开始单张分镜图重生成 taskId={}, imageId={}", taskId, imageId);
                workflowPipelineService.regenerateStoryboardImage(taskId, imageId, overrides);
                log.info("单张分镜图重生成完成 taskId={}, imageId={}", taskId, imageId);
            } catch (Exception e) {
                log.error("单张分镜图重生成失败 taskId={}, imageId={}", taskId, imageId, e);
            }
        }, asyncExecutor);

        return Result.ok();
    }

    /**
     * 单条场景视频重生成（步骤8）。
     * 异步执行，立即返回。
     */
    @PostMapping("/regenerate/scene-video")
    public Result<Void> regenerateSceneVideo(@RequestBody PipelineExecuteRequest request,
                                             @RequestParam Long videoId) {
        if (request.getTaskId() == null) return Result.fail("taskId 不能为空");
        if (videoId == null) return Result.fail("videoId 不能为空");

        log.info("收到单条场景视频重生成请求 taskId={}, videoId={}, overrides={}",
                request.getTaskId(), videoId, request.getOverrides());

        taskInfoProvider.registerTask(
                request.getTaskId(),
                request.getUserId() != null ? request.getUserId() : 1L,
                request.getTitle() != null ? request.getTitle() : "Untitled",
                request.getTaskCreateDTO() != null ? request.getTaskCreateDTO() : new com.comicdrama.common.dto.TaskCreateDTO());

        Long taskId = request.getTaskId();
        java.util.Map<String, Object> overrides = request.getOverrides();
        CompletableFuture.runAsync(() -> {
            try {
                log.info("异步开始单条场景视频重生成 taskId={}, videoId={}", taskId, videoId);
                workflowPipelineService.regenerateSceneVideo(taskId, videoId, overrides);
                log.info("单条场景视频重生成完成 taskId={}, videoId={}", taskId, videoId);
            } catch (Exception e) {
                log.error("单条场景视频重生成失败 taskId={}, videoId={}", taskId, videoId, e);
            }
        }, asyncExecutor);

        return Result.ok();
    }

    /**
     * 清理 [fromStep, toStep] 范围内步骤的产物记录（同步执行）。
     * 用于：暂停时回退当前步骤（rollbackCurrentStep=true）、以及其他需要手动清理下游产物的场景。
     */
    @PostMapping("/clean-artifacts")
    public Result<Void> cleanArtifacts(@RequestBody java.util.Map<String, Object> body,
                                       @RequestParam int fromStep,
                                       @RequestParam int toStep) {
        Object taskIdObj = body.get("taskId");
        if (taskIdObj == null) {
            return Result.fail("taskId 不能为空");
        }
        Long taskId;
        try {
            taskId = Long.valueOf(taskIdObj.toString());
        } catch (Exception e) {
            return Result.fail("taskId 格式错误：" + taskIdObj);
        }
        try {
            workflowPipelineService.cleanArtifacts(taskId, fromStep, toStep);
            return Result.ok();
        } catch (Exception e) {
            log.error("cleanArtifacts 失败 taskId={}, fromStep={}, toStep={}", taskId, fromStep, toStep, e);
            return Result.fail("清理产物失败：" + e.getMessage());
        }
    }

    /**
     * 设置计划暂停标记（「完成此阶段」语义：等当前步执行完毕后自动暂停）。
     * 由 task-service 在用户点击"暂停→完成此阶段"时调用。
     */
    @PostMapping("/set-planned-pause")
    public Result<Void> setPlannedPause(@RequestParam Long taskId,
                                        @RequestParam(defaultValue = "180") int expireMinutes) {
        if (taskId == null) return Result.fail("taskId 不能为空");
        try {
            taskStateManager.setPlannedPause(taskId, true, expireMinutes);
            return Result.ok();
        } catch (Exception e) {
            log.error("setPlannedPause 失败 taskId={}", taskId, e);
            return Result.fail("设置计划暂停失败：" + e.getMessage());
        }
    }

    /** 查询当前计划暂停状态（调试辅助） */
    @GetMapping("/is-planned-pause")
    public Result<Boolean> isPlannedPause(@RequestParam Long taskId) {
        return Result.ok(taskStateManager.isPlannedPause(taskId));
    }

    // ======== 人工审核：手动修改已完成产物 ========

    /** 更新故事摘要（步骤1） */
    @PutMapping("/artifacts/summary")
    public Result<Void> updateSummary(@RequestBody Map<String, Object> body) {
        Long taskId = Long.valueOf(body.get("taskId").toString());
        String outlineText = (String) body.get("outlineText");
        String summary = (String) body.get("summary");
        try {
            storySummaryService.updateSummary(taskId, outlineText, summary);
            return Result.ok();
        } catch (Exception e) {
            log.error("updateSummary 失败 taskId={}", taskId, e);
            return Result.fail("更新故事摘要失败：" + e.getMessage());
        }
    }

    /** 更新单条分镜脚本（步骤2） */
    @PutMapping("/artifacts/storyboard")
    public Result<Void> updateStoryboard(@RequestBody Map<String, Object> body) {
        Long taskId = Long.valueOf(body.get("taskId").toString());
        Long storyboardId = Long.valueOf(body.get("storyboardId").toString());
        @SuppressWarnings("unchecked")
        Map<String, Object> fields = (Map<String, Object>) body.get("fields");
        try {
            storyboardService.updateStoryboard(taskId, storyboardId, fields);
            return Result.ok();
        } catch (Exception e) {
            log.error("updateStoryboard 失败 taskId={}, storyboardId={}", taskId, storyboardId, e);
            return Result.fail("更新分镜脚本失败：" + e.getMessage());
        }
    }

    /** 更新资产设计（步骤3） */
    @PutMapping("/artifacts/asset-design")
    public Result<Void> updateAssetDesign(@RequestBody Map<String, Object> body) {
        Long taskId = Long.valueOf(body.get("taskId").toString());
        Long assetId = Long.valueOf(body.get("assetId").toString());
        @SuppressWarnings("unchecked")
        Map<String, Object> fields = (Map<String, Object>) body.get("fields");
        try {
            assetDesignService.updateAssetDesign(taskId, assetId, fields);
            return Result.ok();
        } catch (Exception e) {
            log.error("updateAssetDesign 失败 taskId={}, assetId={}", taskId, assetId, e);
            return Result.fail("更新资产设计失败：" + e.getMessage());
        }
    }

    /** 替换资产/衍生图片（步骤4/5） */
    @PutMapping("/artifacts/asset-image")
    public Result<Void> replaceAssetImage(@RequestBody Map<String, Object> body) {
        Long taskId = Long.valueOf(body.get("taskId").toString());
        Long imageId = Long.valueOf(body.get("imageId").toString());
        String newImageUrl = (String) body.get("newImageUrl");
        String newThumbnailUrl = (String) body.get("newThumbnailUrl");
        try {
            assetImageService.replaceImage(taskId, imageId, newImageUrl, newThumbnailUrl);
            return Result.ok();
        } catch (Exception e) {
            log.error("replaceAssetImage 失败 taskId={}, imageId={}", taskId, imageId, e);
            return Result.fail("替换图片失败：" + e.getMessage());
        }
    }

    /** 替换分镜图片（步骤6） */
    @PutMapping("/artifacts/storyboard-image")
    public Result<Void> replaceStoryboardImage(@RequestBody Map<String, Object> body) {
        Long taskId = Long.valueOf(body.get("taskId").toString());
        Long imageId = Long.valueOf(body.get("imageId").toString());
        String newImageUrl = (String) body.get("newImageUrl");
        String newThumbnailUrl = (String) body.get("newThumbnailUrl");
        try {
            storyboardImageService.replaceImage(taskId, imageId, newImageUrl, newThumbnailUrl);
            return Result.ok();
        } catch (Exception e) {
            log.error("replaceStoryboardImage 失败 taskId={}, imageId={}", taskId, imageId, e);
            return Result.fail("替换分镜图失败：" + e.getMessage());
        }
    }

    /** 替换配音文件（步骤7） */
    @PutMapping("/artifacts/audio")
    public Result<Void> replaceAudio(@RequestBody Map<String, Object> body) {
        Long taskId = Long.valueOf(body.get("taskId").toString());
        Long audioId = Long.valueOf(body.get("audioId").toString());
        String newAudioUrl = (String) body.get("newAudioUrl");
        try {
            storyboardAudioService.replaceAudio(taskId, audioId, newAudioUrl);
            return Result.ok();
        } catch (Exception e) {
            log.error("replaceAudio 失败 taskId={}, audioId={}", taskId, audioId, e);
            return Result.fail("替换音频失败：" + e.getMessage());
        }
    }

    /** 替换场景视频（步骤8） */
    @PutMapping("/artifacts/video")
    public Result<Void> replaceVideo(@RequestBody Map<String, Object> body) {
        Long taskId = Long.valueOf(body.get("taskId").toString());
        Long videoId = Long.valueOf(body.get("videoId").toString());
        String newVideoUrl = (String) body.get("newVideoUrl");
        String newCoverUrl = (String) body.get("newCoverUrl");
        try {
            sceneVideoService.replaceVideo(taskId, videoId, newVideoUrl, newCoverUrl);
            return Result.ok();
        } catch (Exception e) {
            log.error("replaceVideo 失败 taskId={}, videoId={}", taskId, videoId, e);
            return Result.fail("替换视频失败：" + e.getMessage());
        }
    }
}