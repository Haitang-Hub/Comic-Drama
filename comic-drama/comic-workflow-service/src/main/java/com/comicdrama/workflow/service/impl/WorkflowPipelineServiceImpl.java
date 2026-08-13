package com.comicdrama.workflow.service.impl;

import com.comicdrama.common.broadcast.MessageBroadcaster;
import com.comicdrama.common.broadcast.event.TaskProgressEvent;
import com.comicdrama.common.broadcast.event.TaskStatusChangeEvent;
import com.comicdrama.common.constant.CacheConstants;
import com.comicdrama.common.dto.TaskCreateDTO;
import com.comicdrama.common.exception.BizException;
import com.comicdrama.common.exception.TaskPausedException;
import com.comicdrama.workflow.handler.AbstractStepHandler;
import com.comicdrama.workflow.handler.StepContext;
import com.comicdrama.workflow.handler.StepEnum;
import com.comicdrama.common.service.WorkflowPipelineService;
import com.comicdrama.common.service.WorkflowTaskInfo;
import com.comicdrama.common.service.TaskInfoProvider;
import com.comicdrama.common.service.TaskStateManager;
import com.comicdrama.common.service.ProgressReporter;
import com.comicdrama.common.service.FailureReporter;
import com.comicdrama.common.service.WorkCreator;
import com.comicdrama.common.service.NodeStateManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 工作流流水线服务实现类（Phase-3 增强）。
 *
 * <p>负责编排 7 步流水线的执行：
 * <pre>
 *   1. 获取任务信息 → 2. 标记任务为「生成中」→ 3. 依次执行 7 个步骤 → 4. 标记任务为「已完成」并创建 ComicWork
 * </pre>
 * </p>
 *
 * <h3>Phase-3 新增能力</h3>
 * <ul>
 *   <li>regenerateNode - 清理指定步骤及下游产物 → 重置 node_state → 从该步骤重新执行</li>
 *   <li>resumeFromFailure - 查找最近失败步骤 → 重置 → 从该步骤续跑</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowPipelineServiceImpl implements WorkflowPipelineService {

    private final List<AbstractStepHandler> stepHandlers;
    private final TaskInfoProvider taskInfoProvider;
    private final TaskStateManager taskStateManager;
    private final ProgressReporter progressReporter;
    private final FailureReporter failureReporter;
    private final WorkCreator workCreator;
    private final NodeStateManager nodeStateManager;
    private final MessageBroadcaster broadcaster;
    private final com.comicdrama.workflow.handler.ArtifactLoader artifactLoader;
    private final com.comicdrama.workflow.handler.StepModelBindingResolver bindingResolver;

    // 各步骤产物对应的 Service（用于重新生成时删除旧产物）
    private final com.comicdrama.workflow.service.StorySummaryService storySummaryService;
    private final com.comicdrama.workflow.service.StoryboardService storyboardService;
    private final com.comicdrama.workflow.service.AssetDesignService assetDesignService;
    private final com.comicdrama.workflow.service.AssetImageService assetImageService;
    private final com.comicdrama.workflow.service.StoryboardImageService storyboardImageService;
    private final com.comicdrama.workflow.service.StoryboardAudioService storyboardAudioService;
    private final com.comicdrama.workflow.service.SceneVideoService sceneVideoService;

    @Value("${comic.pipeline.max-steps:9}")
    private int defaultMaxSteps;

    @Override
    public void executePipeline(Long taskId) {
        executeFromStep(taskId, 1, defaultMaxSteps);
    }

    @Override
    public void executeFromStep(Long taskId, int startStep) {
        executeFromStep(taskId, startStep, 9);
    }

    @Override
    public void executeFromStep(Long taskId, int startStep, int maxStep) {
        if (taskId == null) {
            log.error("任务 ID 为空，无法执行流水线");
            throw new IllegalArgumentException("taskId 不能为空");
        }

        if (startStep < 1 || startStep > 9) {
            log.error("起始步骤无效：startStep={}", startStep);
            throw new IllegalArgumentException("startStep 必须在 1-9 之间");
        }

        if (maxStep < 1 || maxStep > 9) {
            log.error("最大步骤无效：maxStep={}", maxStep);
            throw new IllegalArgumentException("maxStep 必须在 1-9 之间");
        }

        log.info("========== 开始执行流水线 taskId={}, startStep={}, maxStep={} ==========", taskId, startStep, maxStep);
        long pipelineStart = System.currentTimeMillis();
        LocalDateTime startTime = LocalDateTime.now();

        // 前置验证：检查模型配置（不纳入 try-catch，直接将错误传递给调用方）
        WorkflowTaskInfo taskInfo = taskInfoProvider.getTaskInfo(taskId);
        if (taskInfo == null) {
            log.error("任务不存在 taskId={}", taskId);
            throw new RuntimeException("任务不存在：taskId=" + taskId);
        }

        if (startStep <= 1) {
            validateModelConfigurations(taskInfo);
        }

        try {
            taskStateManager.markAsRunning(taskId, startStep, startTime);
            // 进入执行前先清除计划暂停标记，避免"刚跑就停"
            taskStateManager.clearPlannedPause(taskId);

            StepContext context = buildStepContext(taskInfo);

            // 产物清理策略：
            // - regenerateNode：级联清理 [step, 9] 全部下游（上游输入已改变，下游旧产物作废）
            // - executeFromStep 循环内：每步执行前清理该步旧产物（确保 resolveBatchStartIndex 不跳过）
            // - resume（自动模式续跑）：无需额外清理，因下游产物已被 regenerateNode 清理或本就不存在

            // 重置 startStep 及以后的节点状态（必要：否则失败状态会导致跳过）
            nodeStateManager.resetNodeStatesFrom(taskId, startStep);

            // 从数据库恢复之前步骤的产物（[1, startStep-1] 范围的已完成步骤）
            if (startStep > 1) {
                artifactLoader.loadArtifacts(context, taskId, startStep);
            }

            List<AbstractStepHandler> sortedHandlers = getSortedHandlers();

            for (AbstractStepHandler handler : sortedHandlers) {
                StepEnum step = handler.getStep();

                if (step.getOrder() < startStep) {
                    log.info("跳过已完成步骤：{} (order={})", step.getName(), step.getOrder());
                    continue;
                }

                if (step.getOrder() > maxStep) {
                    log.info("跳过超出 maxStep 的步骤：{} (order={}, maxStep={})", step.getName(), step.getOrder(), maxStep);
                    continue;
                }

                // 检查步骤是否应该被跳过（如 AUDIO 步骤在配音关闭时跳过）
                if (shouldSkipStep(step, taskInfo.requestDTO())) {
                    log.info("---------- 跳过可选步骤：{} (order={}, 原因：配置禁用) ----------",
                            step.getName(), step.getOrder());

                    LocalDateTime skipTime = LocalDateTime.now();
                    int totalProgress = calculateTotalProgress(step.getOrder(), 100);

                    // 记录跳过状态（status=6 表示跳过）
                    nodeStateManager.saveNodeState(taskId, step.getOrder(), 6,
                            skipTime, skipTime, 0L, null, "步骤已跳过（AI配音已关闭）");

                    taskStateManager.updateStepProgress(taskId, step.getOrder(), 100, totalProgress);

                    broadcaster.publish(CacheConstants.CHANNEL_TASK_PROGRESS,
                            new TaskProgressEvent(this, taskId, step.getOrder(), step.getCode(),
                                    100, totalProgress, step.getName() + "已跳过"));

                    log.info("---------- 跳过完成：{}，totalProgress={}% ----------",
                            step.getName(), totalProgress);
                    continue;
                }

                // 清理当前步骤的旧产物，确保 resolveBatchStartIndex 不会因旧产物而跳过项目。
                // 场景：上游步骤被重新生成后，下游步骤的旧产物已过期，需要清理后重新生成。
                // 即使首次执行（无旧产物），清理也是安全的（空操作）。
                deleteStepArtifacts(taskId, step.getOrder(), step.getOrder());
                log.info("---------- 已清理步骤{}旧产物，开始执行 ----------", step.getName());

                log.info("---------- 执行步骤：{} (order={}, code={}) ----------",
                        step.getName(), step.getOrder(), step.getCode());

                LocalDateTime stepStart = LocalDateTime.now();
                long stepStartMs = System.currentTimeMillis();

                // 更新 currentStep 为即将执行的步骤，确保用户暂停时能读取到正确的步骤号。
                // 不能用 markAsRunning（它会把 currentStep 减 1），改用 updateStepProgress 直接设置。
                taskStateManager.updateStepProgress(taskId, step.getOrder(), 0,
                        calculateTotalProgress(step.getOrder(), 0));

                nodeStateManager.saveNodeState(taskId, step.getOrder(), 1,
                        stepStart, null, null, null, null);

                handler.execute(context);

                long stepEndMs = System.currentTimeMillis();
                long durationMs = stepEndMs - stepStartMs;
                LocalDateTime stepEnd = LocalDateTime.now();

                nodeStateManager.saveNodeState(taskId, step.getOrder(), 2,
                        stepStart, stepEnd, durationMs, null, null);

                int totalProgress = calculateTotalProgress(step.getOrder(), 100);
                taskStateManager.updateStepProgress(taskId, step.getOrder(), 100, totalProgress);

                log.info("---------- 步骤完成：{}，totalProgress={}%，耗时={}ms ----------",
                        step.getName(), totalProgress, durationMs);

                // ========== 步骤边界：判断是否需要暂停 ==========
                // 优先级：人工审核 > 用户主动"完成此阶段"(计划暂停)
                boolean shouldPause = false;
                String pauseReason = null;

                // 1. 人工审核模式：每个步骤完成后暂停等待审核（最后一步除外）
                Integer execMode = taskInfo.requestDTO() != null ? taskInfo.requestDTO().getExecMode() : 0;
                if (execMode != null && execMode == 1 && step.getOrder() < maxStep) {
                    shouldPause = true;
                    pauseReason = "人工审核模式：步骤完成，等待审核";
                }

                // 2. 计划暂停：用户点击暂停时选了"完成此阶段"，等当前步跑完后停
                if (!shouldPause && step.getOrder() < maxStep && taskStateManager.isPlannedPause(taskId)) {
                    shouldPause = true;
                    pauseReason = "完成此阶段：当前步骤完成后暂停";
                    taskStateManager.clearPlannedPause(taskId); // 一次性消费，避免再次生效
                }

                if (shouldPause) {
                    log.info("========== 步骤{}完成后暂停 taskId={}, 原因：{} ==========",
                            step.getName(), taskId, pauseReason);
                    taskStateManager.markAsPaused(taskId, step.getOrder(), LocalDateTime.now());
                    broadcaster.publish(CacheConstants.CHANNEL_TASK_STATUS,
                            new TaskStatusChangeEvent(this, taskId, 1, 4));
                    return; // 退出 handler 循环
                }
            }

            long pipelineCostMs = System.currentTimeMillis() - pipelineStart;
            int totalConsumeTime = (int) (pipelineCostMs / 1000);

            log.info("========== 流水线执行完成 taskId={}, maxStep={}, startStep={}, 总耗时={}ms ==========",
                    taskId, maxStep, startStep, pipelineCostMs);

            // 仅当 maxStep == 9（完整跑完整条流水线）时才标记任务为 DONE，创建作品并广播完成事件。
            // 单步重生成（startStep == maxStep && maxStep < 9）或从某步续跑未到末端时，不能直接标 DONE，
            // 否则会出现「重新生成步骤4→立即显示进度100%已完成」的 Bug。
            if (maxStep >= 9) {
                // 从步骤9产物中读取成片信息（由 VideoMergeStepHandler 生成）
                com.comicdrama.workflow.dto.FinalWorkInfo finalInfo = context.getArtifact(StepEnum.VIDEO_MERGE);
                String coverUrl = finalInfo != null ? finalInfo.getCoverUrl() : null;
                String finalVideoUrl = finalInfo != null ? finalInfo.getFinalVideoUrl() : null;
                String manifestJson = finalInfo != null ? finalInfo.getManifestJson() : null;

                taskStateManager.markAsDone(taskId, 100, totalConsumeTime,
                        coverUrl, finalVideoUrl, LocalDateTime.now(), maxStep, manifestJson);

                // ComicWork 已由 VideoMergeStepHandler 通过 RestTemplate 调用 resource-service 创建
                if (finalInfo != null && finalInfo.getWorkId() != null) {
                    log.info("ComicWork 已创建 workId={}, taskId={}", finalInfo.getWorkId(), taskId);
                } else {
                    log.warn("ComicWork 创建可能失败，taskId={}", taskId);
                }

                broadcaster.publish(CacheConstants.CHANNEL_TASK_STATUS,
                        new TaskStatusChangeEvent(this, taskId, 1, 2));
            } else if (startStep == maxStep) {
                // 单步重生成成功：回到「已暂停」状态，等待用户进一步决定（续跑下一步/继续重生成当前步骤）
                int totalProgress = calculateTotalProgress(maxStep, 100);
                taskStateManager.markAsPaused(taskId, maxStep, LocalDateTime.now());
                taskStateManager.updateStepProgress(taskId, maxStep, 100, totalProgress);
                broadcaster.publish(CacheConstants.CHANNEL_TASK_STATUS,
                        new TaskStatusChangeEvent(this, taskId, 1, 4));
                log.info("单步重生成完成，回到已暂停状态 taskId={}, step={}, totalProgress={}%",
                        taskId, maxStep, totalProgress);
            } else {
                // 从某步续跑但未到末端（例如 startStep=4, maxStep=8）：保持 RUNNING 但不要 DONE
                // 按现在的流水线语义不会走到这里，但兜底留 log，避免误标 DONE
                log.info("局部续跑结束但未到步骤9，保持原状态不广播DONE taskId={}, startStep={}, maxStep={}",
                        taskId, startStep, maxStep);
            }

        } catch (TaskPausedException e) {
            long pipelineCostMs = System.currentTimeMillis() - pipelineStart;
            log.info("========== 流水线暂停 taskId={}, 总耗时={}ms, step={} ==========",
                    taskId, pipelineCostMs, e.getStepOrder());

            int pausedStep = e.getStepOrder();
            // 检查任务是否已被用户暂停（可能含回退操作）。
            // 如果已暂停，用户已设置正确的 currentStep 和 node_state，不要覆盖。
            // 否则（系统自动暂停等场景），正常标记暂停。
            int currentStatus = taskStateManager.getStatus(taskId);
            if (currentStatus != 4) { // 4 = PAUSED
                nodeStateManager.saveNodeState(taskId, pausedStep, 5,
                        null, LocalDateTime.now(), null, null, "任务已暂停");
                taskStateManager.markAsPaused(taskId, pausedStep, LocalDateTime.now());
            } else {
                log.info("任务已被用户暂停（可能含回退），保留用户设置的 currentStep，不覆盖 taskId={}", taskId);
            }

            broadcaster.publish(CacheConstants.CHANNEL_TASK_STATUS,
                    new TaskStatusChangeEvent(this, taskId, 1, 4));

        } catch (Exception e) {
            long pipelineCostMs = System.currentTimeMillis() - pipelineStart;
            log.error("========== 流水线执行失败 taskId={}, 总耗时={}ms ==========", taskId, pipelineCostMs, e);

            int failureStep = resolveFailureStep(e, startStep);
            String failureStepCode = resolveFailureStepCode(e, failureStep);

            nodeStateManager.saveNodeState(taskId, failureStep, 3,
                    null, LocalDateTime.now(), null, null, e.getMessage());

            failureReporter.reportFailure(taskId, failureStep, failureStepCode,
                    e.getMessage(), e);

            taskStateManager.markAsFailed(taskId, failureStep,
                    e.getMessage(), getStackTraceString(e), LocalDateTime.now());

            broadcaster.publish(CacheConstants.CHANNEL_TASK_STATUS,
                    new TaskStatusChangeEvent(this, taskId, 1, 3));

            throw new RuntimeException("流水线执行失败：taskId=" + taskId, e);
        }
    }

    @Override
    public void regenerateNode(Long taskId, int stepOrder, Map<String, Object> overrides) {
        if (taskId == null) {
            throw new IllegalArgumentException("taskId 不能为空");
        }
        if (stepOrder < 1 || stepOrder > 9) {
            throw new IllegalArgumentException("stepOrder 必须在 1-9 之间");
        }

        // 【后端安全校验】仅允许在「已暂停(4) / 失败(3) / 已完成(2)」时重生成，
        // 「排队(0) / 生成中(1)」时禁止调用，避免与正常流水线并发执行引起数据错乱。
        int curStatus = taskStateManager.getStatus(taskId);
        if (curStatus == 0 || curStatus == 1) {
            String curStatusLabel = curStatus == 0 ? "排队中" : "生成中";
            throw new BizException("任务当前为【" + curStatusLabel + "】状态，请先暂停或等待结束后再重新生成");
        }
        if (curStatus == -1) {
            throw new BizException("任务不存在：taskId=" + taskId);
        }

        log.info("========== 节点重生成 taskId={}, stepOrder={}, overrides={}, 当前状态={} (仅重新生成单步) ==========",
                taskId, stepOrder, overrides, curStatus);

        // 0. 将 overrides 中的 artStyle/visualStyle 应用到 TaskInfoProvider 缓存的 DTO 中
        if (overrides != null && !overrides.isEmpty()) {
            WorkflowTaskInfo taskInfo = taskInfoProvider.getTaskInfo(taskId);
            if (taskInfo != null) {
                TaskCreateDTO dto = taskInfo.requestDTO();
                if (dto == null) dto = new TaskCreateDTO();
                boolean changed = false;
                if (overrides.containsKey("artStyle")) {
                    dto.setArtStyle((String) overrides.get("artStyle"));
                    changed = true;
                }
                if (overrides.containsKey("visualStyle")) {
                    dto.setVisualStyle((String) overrides.get("visualStyle"));
                    changed = true;
                }
                if (changed) {
                    // 重新注册以刷新缓存
                    taskInfoProvider.registerTask(taskId, taskInfo.userId(), taskInfo.title(), dto);
                    log.info("[regenerateNode] 已将 overrides 应用到 taskInfo 缓存 dto, taskId={}", taskId);
                }
            }
        }

        // 1. 删除数据库旧产物：任何步骤重生成都会导致下游产物失效，必须级联清理。
        // 步骤4(资产绘图)变了 → 步骤5(衍生)6(分镜图)8(视频)9(合并)全部作废
        // 步骤6(分镜绘图)变了 → 步骤8(视频)9(合并)作废
        // 以此类推。
        int cleanFrom = stepOrder;
        int cleanTo = 9;
        log.info("[regenerateNode] 级联清理步骤{}~{}的旧产物 taskId={}", cleanFrom, cleanTo, taskId);
        deleteStepArtifacts(taskId, cleanFrom, cleanTo);

        // 2. 重置 [cleanFrom, 9] 的节点状态
        nodeStateManager.resetNodeStatesFrom(taskId, cleanFrom);

        // 3. 重新执行该步骤（单步：startStep == maxStep == stepOrder）
        executeFromStep(taskId, stepOrder, stepOrder);

        log.info("========== 节点重生成完成 taskId={}, stepOrder={} ==========", taskId, stepOrder);
    }

    /**
     * 删除 [fromStep, toStep] 范围内步骤的产物记录（用于重新生成/续跑前的清理）。
     *
     * 清理策略：
     * - 单步重生成（fromStep == toStep == stepOrder）：仅删除指定步骤产物，不影响其他步骤（用户需求）
     * - 续跑多个步骤（fromStep < toStep）：级联删除下游所有依赖步骤产物，
     *     避免各 Handler.resolveBatchStartIndex 从 DB 统计旧记录数后将所有条目全部跳过，
     *     导致"步骤4资产绘图成功 0/3、步骤5成功 0/N"等 Bug。
     */
    private void deleteStepArtifacts(Long taskId, int fromStep, int toStep) {
        if (fromStep < 1) fromStep = 1;
        if (toStep > 9) toStep = 9;
        if (fromStep > toStep) {
            log.warn("deleteStepArtifacts: fromStep={} > toStep={}, 无需清理, taskId={}", fromStep, toStep, taskId);
            return;
        }
        log.info("[deleteStepArtifacts] 开始清理步骤{}~{}产物, taskId={}", fromStep, toStep, taskId);

        // 按步骤顺序从上游到下游依次判断是否在清理范围内
        // 步骤1: 故事摘要 (order=1)
        if (1 >= fromStep && 1 <= toStep) {
            try {
                storySummaryService.lambdaUpdate()
                        .eq(com.comicdrama.workflow.entity.StorySummary::getTaskId, taskId)
                        .remove();
                log.info("[deleteStepArtifacts] 已删除步骤1[SUMMARY]旧产物, taskId={}", taskId);
            } catch (Exception e) { logCleanupFail(1, e, taskId); }
        }

        // 步骤2: 分镜脚本 (order=2)
        if (2 >= fromStep && 2 <= toStep) {
            try {
                storyboardService.lambdaUpdate()
                        .eq(com.comicdrama.workflow.entity.Storyboard::getTaskId, taskId)
                        .remove();
                log.info("[deleteStepArtifacts] 已删除步骤2[STORYBOARD]旧产物, taskId={}", taskId);
            } catch (Exception e) { logCleanupFail(2, e, taskId); }
        }

        // 步骤3: 资产设计 (order=3)
        if (3 >= fromStep && 3 <= toStep) {
            try {
                assetDesignService.lambdaUpdate()
                        .eq(com.comicdrama.workflow.entity.AssetDesign::getTaskId, taskId)
                        .remove();
                log.info("[deleteStepArtifacts] 已删除步骤3[ASSET_DESIGN]旧产物, taskId={}", taskId);
            } catch (Exception e) { logCleanupFail(3, e, taskId); }
        }

        // 步骤4: 资产绘图 (首版资产图，base_image_id 为 NULL) (order=4)
        if (4 >= fromStep && 4 <= toStep) {
            try {
                assetImageService.lambdaUpdate()
                        .eq(com.comicdrama.workflow.entity.AssetImage::getTaskId, taskId)
                        .isNull(com.comicdrama.workflow.entity.AssetImage::getBaseImageId)
                        .remove();
                log.info("[deleteStepArtifacts] 已删除步骤4[ASSET_IMAGE]旧产物(首版资产图), taskId={}", taskId);
            } catch (Exception e) { logCleanupFail(4, e, taskId); }
        }

        // 步骤5: 衍生绘图 (衍生资产图，base_image_id 不为 NULL) (order=5)
        if (5 >= fromStep && 5 <= toStep) {
            try {
                assetImageService.lambdaUpdate()
                        .eq(com.comicdrama.workflow.entity.AssetImage::getTaskId, taskId)
                        .isNotNull(com.comicdrama.workflow.entity.AssetImage::getBaseImageId)
                        .remove();
                log.info("[deleteStepArtifacts] 已删除步骤5[ASSET_DERIVE]旧产物(衍生资产图), taskId={}", taskId);
            } catch (Exception e) { logCleanupFail(5, e, taskId); }
        }

        // 步骤6: 分镜绘图 (order=6)
        if (6 >= fromStep && 6 <= toStep) {
            try {
                storyboardImageService.lambdaUpdate()
                        .eq(com.comicdrama.workflow.entity.StoryboardImage::getTaskId, taskId)
                        .remove();
                log.info("[deleteStepArtifacts] 已删除步骤6[STORYBOARD_IMAGE]旧产物, taskId={}", taskId);
            } catch (Exception e) { logCleanupFail(6, e, taskId); }
        }

        // 步骤7: 配音合成 (order=7)
        if (7 >= fromStep && 7 <= toStep) {
            try {
                storyboardAudioService.lambdaUpdate()
                        .eq(com.comicdrama.workflow.entity.StoryboardAudio::getTaskId, taskId)
                        .remove();
                log.info("[deleteStepArtifacts] 已删除步骤7[AUDIO]旧产物, taskId={}", taskId);
            } catch (Exception e) { logCleanupFail(7, e, taskId); }
        }

        // 步骤8: 视频生成 (order=8)
        if (8 >= fromStep && 8 <= toStep) {
            try {
                sceneVideoService.lambdaUpdate()
                        .eq(com.comicdrama.workflow.entity.SceneVideo::getTaskId, taskId)
                        .remove();
                log.info("[deleteStepArtifacts] 已删除步骤8[VIDEO]旧产物, taskId={}", taskId);
            } catch (Exception e) { logCleanupFail(8, e, taskId); }
        }

        // 步骤9: 视频合并 (order=9) — 无独立产物表
        if (9 >= fromStep && 9 <= toStep) {
            log.info("[deleteStepArtifacts] 步骤9[VIDEO_MERGE]无需删除产物(合并结果), taskId={}", taskId);
        }
    }

    private void logCleanupFail(int stepOrder, Exception e, Long taskId) {
        log.warn("[deleteStepArtifacts] 删除步骤{}旧产物失败，继续执行: taskId={}, error={}",
                stepOrder, taskId, e.getMessage());
    }

    /**
     * 公开的清理接口：删除 [fromStep, toStep] 范围内步骤的产物（task-service 的 pause(rollback=true) 会调用）。
     */
    public void cleanArtifacts(Long taskId, int fromStep, int toStep) {
        if (taskId == null) {
            log.warn("cleanArtifacts: taskId 为空，跳过");
            return;
        }
        deleteStepArtifacts(taskId, fromStep, toStep);
    }

    @Override
    public void resumeFromFailure(Long taskId) {
        if (taskId == null) {
            throw new IllegalArgumentException("taskId 不能为空");
        }

        log.info("========== 断点续跑 taskId={} ==========", taskId);

        Integer resumeStep = nodeStateManager.findResumeStep(taskId);

        if (resumeStep == null) {
            log.warn("未找到未完成的节点，任务 taskId={}", taskId);
            throw new BizException("所有步骤已完成，无需断点续跑");
        }

        log.info("找到续跑起始步骤：stepOrder={}, taskId={}", resumeStep, taskId);

        nodeStateManager.resetNodeStatesFrom(taskId, resumeStep);

        executeFromStep(taskId, resumeStep);

        log.info("========== 断点续跑完成 taskId={}, fromStep={} ==========", taskId, resumeStep);
    }

    // ==================== 单张产物重生成 ====================

    /** 单张重生成通用的暂停状态校验（与 regenerateNode 保持一致）。 */
    private int validateSingleRegenerateReady(Long taskId, String opName) {
        int curStatus = taskStateManager.getStatus(taskId);
        if (curStatus == 0 || curStatus == 1) {
            String curStatusLabel = curStatus == 0 ? "排队中" : "生成中";
            throw new BizException("任务当前为【" + curStatusLabel + "】状态，请先暂停或等待结束后再" + opName);
        }
        if (curStatus == -1) {
            throw new BizException("任务不存在：taskId=" + taskId);
        }
        return curStatus;
    }

    @Override
    public void regenerateAssetImage(Long taskId, Long imageId, Map<String, Object> overrides) {
        if (taskId == null || imageId == null) {
            throw new IllegalArgumentException("taskId 与 imageId 不能为空");
        }

        String opName = "重新生成单张资产图";
        log.info("========== {} taskId={}, imageId={}, overrides={} ==========", opName, taskId, imageId, overrides);
        int curStatus = validateSingleRegenerateReady(taskId, opName);

        // 1. 根据 imageId 查出旧资产图记录（区分步骤4首版 / 步骤5衍生）
        com.comicdrama.workflow.entity.AssetImage oldImg = assetImageService.getById(imageId);
        if (oldImg == null) {
            throw new BizException("资产图不存在：imageId=" + imageId);
        }
        if (!taskId.equals(oldImg.getTaskId())) {
            throw new BizException("资产图归属任务不匹配，拒绝重生成");
        }

        StepEnum targetStep;
        if (oldImg.getBaseImageId() == null) {
            targetStep = StepEnum.ASSET_IMAGE;     // 步骤4
        } else {
            targetStep = StepEnum.ASSET_DERIVE;    // 步骤5
        }

        // 2. 删除旧记录（仅这一张）
        assetImageService.removeById(imageId);
        log.info("[单张重生成] 已删除{}旧资产图，imageId={}, assetName={}, taskId={}",
                targetStep.getName(), imageId, oldImg.getAssetName(), taskId);

        // 3. 构造上下文 + 加载前置产物（必须保留首版资产图等给步骤5使用，所以 startStep = targetStep）
        try {
            taskStateManager.markAsRunning(taskId, targetStep.getOrder(), LocalDateTime.now());

            WorkflowTaskInfo taskInfo = taskInfoProvider.getTaskInfo(taskId);
            if (taskInfo == null) throw new BizException("任务不存在：taskId=" + taskId);

            StepContext context = buildStepContext(taskInfo);
            if (overrides != null && !overrides.isEmpty()) {
                context.setOverrides(overrides);
            }
            artifactLoader.loadArtifacts(context, taskId, targetStep.getOrder());

            // 4. 定位对应 Handler
            AbstractStepHandler handler = stepHandlers.stream()
                    .filter(h -> h.getStep() == targetStep)
                    .findFirst()
                    .orElseThrow(() -> new BizException("找不到步骤处理器：" + targetStep.getName()));

            // 5. 从前置产物中找到对应的源数据（AssetDesign）
            com.comicdrama.workflow.entity.AssetDesign sourceAsset = null;
            if (oldImg.getAssetId() != null) {
                java.util.List<com.comicdrama.workflow.entity.AssetDesign> designs = context.getArtifact(StepEnum.ASSET_DESIGN);
                if (designs != null) {
                    sourceAsset = designs.stream()
                            .filter(d -> oldImg.getAssetId().equals(d.getId()))
                            .findFirst()
                            .orElse(null);
                }
            }
            if (sourceAsset == null) {
                throw new BizException("无法定位该图片对应的资产设计（assetId=" + oldImg.getAssetId() + "），无法重生成");
            }

            // 5.1 应用覆盖参数到 sourceAsset 和 requestDTO
            if (overrides != null && !overrides.isEmpty()) {
                if (overrides.containsKey("assetDesc")) {
                    sourceAsset.setAssetDesc((String) overrides.get("assetDesc"));
                }
                if (overrides.containsKey("assetName")) {
                    sourceAsset.setAssetName((String) overrides.get("assetName"));
                }
                // 覆盖画风/视觉风格（存储在 requestDTO 中）
                TaskCreateDTO dto = context.getRequestDTO();
                if (dto != null) {
                    if (overrides.containsKey("artStyle")) {
                        dto.setArtStyle((String) overrides.get("artStyle"));
                    }
                    if (overrides.containsKey("visualStyle")) {
                        dto.setVisualStyle((String) overrides.get("visualStyle"));
                    }
                }
                log.info("[单张重生成] 已应用覆盖参数: {}", overrides);
            }

            // 6. 步骤5 单张重生成：确保参考图（上一版本图片）在 context 的 ASSET_IMAGE 列表中，
            //    这样 AssetDeriveStepHandler.processBatchItem 用 asset.derivedFrom 匹配 assetName 时才找得到。
            if (targetStep == StepEnum.ASSET_DERIVE && oldImg.getBaseImageId() != null) {
                java.util.List<com.comicdrama.workflow.entity.AssetImage> baseImages = context.getArtifact(StepEnum.ASSET_IMAGE);
                if (baseImages == null) {
                    baseImages = new java.util.ArrayList<>();
                    context.putArtifact(StepEnum.ASSET_IMAGE, baseImages);
                }
                // 在已加载的 baseImages 中查找 ID 匹配
                com.comicdrama.workflow.entity.AssetImage baseImage = baseImages.stream()
                        .filter(b -> oldImg.getBaseImageId().equals(b.getId()))
                        .findFirst()
                        .orElse(null);
                if (baseImage == null) {
                    baseImage = assetImageService.getById(oldImg.getBaseImageId());
                    if (baseImage != null) {
                        // 补入 context，保证名称映射可命中
                        baseImages.add(baseImage);
                    }
                }
                if (baseImage == null) {
                    throw new BizException("衍生图参考图不存在(baseImageId=" + oldImg.getBaseImageId()
                            + ")，请先重生成上一版图片");
                }
                // 同样把 ASSET_DERIVE 的列表初始化好，避免保存时报 NPE
                if (context.getArtifact(StepEnum.ASSET_DERIVE) == null) {
                    context.putArtifact(StepEnum.ASSET_DERIVE, new java.util.ArrayList<>(baseImages));
                }
            }

            nodeStateManager.saveNodeState(taskId, targetStep.getOrder(), 1, LocalDateTime.now(),
                    null, null, null, null);

            // 7. 单条生成 + 保存
            java.time.LocalDateTime stepStart = LocalDateTime.now();
            long stepStartMs = System.currentTimeMillis();

            Object result = handler.processBatchItem(sourceAsset, 0, context);
            if (result == null) {
                throw new BizException(opName + "失败：AI 返回空结果，请稍后重试");
            }
            handler.saveBatchResult(sourceAsset, result, context);

            long stepEndMs = System.currentTimeMillis();
            long durationMs = stepEndMs - stepStartMs;
            nodeStateManager.saveNodeState(taskId, targetStep.getOrder(), 2, stepStart,
                    LocalDateTime.now(), durationMs, null, null);

            int totalProgress = calculateTotalProgress(targetStep.getOrder(), 100);
            taskStateManager.updateStepProgress(taskId, targetStep.getOrder(), 100, totalProgress);

            broadcaster.publish(com.comicdrama.common.constant.CacheConstants.CHANNEL_TASK_PROGRESS,
                    new TaskProgressEvent(this, taskId, targetStep.getOrder(), targetStep.getCode(),
                            100, totalProgress, "单张资产图已重生成"));

            // 8. 回到暂停（或原失败/已完成）状态
            markBackToIdleState(taskId, curStatus, targetStep.getOrder(), totalProgress);

            log.info("========== {}完成 taskId={}, imageId={} ==========", opName, taskId, imageId);
        } catch (BizException e) {
            log.warn("{}业务异常 taskId={}, imageId={}: {}", opName, taskId, imageId, e.getMessage());
            // 先恢复任务到可操作状态，再报告失败（确保失败状态不被 markBackToIdleState 覆盖）
            try { markBackToIdleState(taskId, curStatus, targetStep == null ? 0 : targetStep.getOrder(), null); } catch (Exception ignore) {}
            try {
                failureReporter.reportFailure(taskId, targetStep == null ? 0 : targetStep.getOrder(),
                        opName + "失败", e.getMessage(), e);
            } catch (Exception ignore) {}
            throw e;
        } catch (Exception e) {
            log.error("{}失败 taskId={}, imageId={}", opName, taskId, imageId, e);
            try { markBackToIdleState(taskId, curStatus, targetStep == null ? 0 : targetStep.getOrder(), null); } catch (Exception ignore) {}
            try {
                failureReporter.reportFailure(taskId, targetStep == null ? 0 : targetStep.getOrder(),
                        opName + "失败", e.getMessage(), e);
            } catch (Exception ignore) {}
            throw new BizException(opName + "失败：" + e.getMessage());
        }
    }

    @Override
    public void regenerateStoryboardImage(Long taskId, Long imageId, Map<String, Object> overrides) {
        if (taskId == null || imageId == null) {
            throw new IllegalArgumentException("taskId 与 imageId 不能为空");
        }

        String opName = "重新生成单张分镜图";
        StepEnum targetStep = StepEnum.STORYBOARD_IMAGE;
        log.info("========== {} taskId={}, imageId={}, overrides={} ==========", opName, taskId, imageId, overrides);
        int curStatus = validateSingleRegenerateReady(taskId, opName);

        // 1. 查出旧分镜图
        com.comicdrama.workflow.entity.StoryboardImage oldImg = storyboardImageService.getById(imageId);
        if (oldImg == null) {
            throw new BizException("分镜图不存在：imageId=" + imageId);
        }
        if (!taskId.equals(oldImg.getTaskId())) {
            throw new BizException("分镜图归属任务不匹配，拒绝重生成");
        }

        // 2. 删除旧图（仅该单张）
        storyboardImageService.removeById(imageId);
        log.info("[单张重生成] 已删除{}旧分镜图，imageId={}, storyboardId={}, taskId={}",
                targetStep.getName(), imageId, oldImg.getStoryboardId(), taskId);

        try {
            taskStateManager.markAsRunning(taskId, targetStep.getOrder(), LocalDateTime.now());

            WorkflowTaskInfo taskInfo = taskInfoProvider.getTaskInfo(taskId);
            if (taskInfo == null) throw new BizException("任务不存在：taskId=" + taskId);

            StepContext context = buildStepContext(taskInfo);
            if (overrides != null && !overrides.isEmpty()) {
                context.setOverrides(overrides);
            }
            artifactLoader.loadArtifacts(context, taskId, targetStep.getOrder());

            AbstractStepHandler handler = stepHandlers.stream()
                    .filter(h -> h.getStep() == targetStep)
                    .findFirst()
                    .orElseThrow(() -> new BizException("找不到步骤处理器：" + targetStep.getName()));

            // 3. 找到对应 Storyboard 源数据
            com.comicdrama.workflow.entity.Storyboard sourceSb = null;
            if (oldImg.getStoryboardId() != null) {
                java.util.List<com.comicdrama.workflow.entity.Storyboard> storyboards = context.getArtifact(StepEnum.STORYBOARD);
                if (storyboards != null) {
                    sourceSb = storyboards.stream()
                            .filter(s -> oldImg.getStoryboardId().equals(s.getId()))
                            .findFirst()
                            .orElse(null);
                }
            }
            if (sourceSb == null) {
                // 兜底：直接查 storyboard 表
                sourceSb = storyboardService.getById(oldImg.getStoryboardId());
            }
            if (sourceSb == null) {
                throw new BizException("无法定位该图片对应的分镜脚本（storyboardId=" + oldImg.getStoryboardId() + "），无法重生成");
            }

            // 3.1 应用覆盖参数到 sourceSb 和 requestDTO
            if (overrides != null && !overrides.isEmpty()) {
                if (overrides.containsKey("visualDesc")) {
                    sourceSb.setVisualDesc((String) overrides.get("visualDesc"));
                }
                // 覆盖画风/视觉风格（存储在 requestDTO 中）
                TaskCreateDTO dto = context.getRequestDTO();
                if (dto != null) {
                    if (overrides.containsKey("artStyle")) {
                        dto.setArtStyle((String) overrides.get("artStyle"));
                    }
                    if (overrides.containsKey("visualStyle")) {
                        dto.setVisualStyle((String) overrides.get("visualStyle"));
                    }
                }
                log.info("[单张重生成] 已应用覆盖参数: {}", overrides);
            }

            nodeStateManager.saveNodeState(taskId, targetStep.getOrder(), 1, LocalDateTime.now(),
                    null, null, null, null);

            java.time.LocalDateTime stepStart = LocalDateTime.now();
            long stepStartMs = System.currentTimeMillis();

            Object result = handler.processBatchItem(sourceSb, 0, context);
            if (result == null) {
                throw new BizException(opName + "失败：AI 返回空结果，请稍后重试");
            }
            handler.saveBatchResult(sourceSb, result, context);

            long durationMs = System.currentTimeMillis() - stepStartMs;
            nodeStateManager.saveNodeState(taskId, targetStep.getOrder(), 2, stepStart,
                    LocalDateTime.now(), durationMs, null, null);

            int totalProgress = calculateTotalProgress(targetStep.getOrder(), 100);
            taskStateManager.updateStepProgress(taskId, targetStep.getOrder(), 100, totalProgress);

            broadcaster.publish(com.comicdrama.common.constant.CacheConstants.CHANNEL_TASK_PROGRESS,
                    new TaskProgressEvent(this, taskId, targetStep.getOrder(), targetStep.getCode(),
                            100, totalProgress, "单张分镜图已重生成"));

            markBackToIdleState(taskId, curStatus, targetStep.getOrder(), totalProgress);

            log.info("========== {}完成 taskId={}, imageId={} ==========", opName, taskId, imageId);
        } catch (BizException e) {
            log.warn("{}业务异常 taskId={}, imageId={}: {}", opName, taskId, imageId, e.getMessage());
            try { markBackToIdleState(taskId, curStatus, targetStep.getOrder(), null); } catch (Exception ignore) {}
            try { failureReporter.reportFailure(taskId, targetStep.getOrder(), opName + "失败", e.getMessage(), e); } catch (Exception ignore) {}
            throw e;
        } catch (Exception e) {
            log.error("{}失败 taskId={}, imageId={}", opName, taskId, imageId, e);
            try { markBackToIdleState(taskId, curStatus, targetStep.getOrder(), null); } catch (Exception ignore) {}
            try { failureReporter.reportFailure(taskId, targetStep.getOrder(), opName + "失败", e.getMessage(), e); } catch (Exception ignore) {}
            throw new BizException(opName + "失败：" + e.getMessage());
        }
    }

    @Override
    public void regenerateSceneVideo(Long taskId, Long videoId, Map<String, Object> overrides) {
        if (taskId == null || videoId == null) {
            throw new IllegalArgumentException("taskId 与 videoId 不能为空");
        }

        String opName = "重新生成单条场景视频";
        StepEnum targetStep = StepEnum.VIDEO;
        log.info("========== {} taskId={}, videoId={}, overrides={} ==========", opName, taskId, videoId, overrides);
        int curStatus = validateSingleRegenerateReady(taskId, opName);

        // 1. 查出旧视频
        com.comicdrama.workflow.entity.SceneVideo oldVideo = sceneVideoService.getById(videoId);
        if (oldVideo == null) {
            throw new BizException("场景视频不存在：videoId=" + videoId);
        }
        if (!taskId.equals(oldVideo.getTaskId())) {
            throw new BizException("场景视频归属任务不匹配，拒绝重生成");
        }

        Long targetGroupId = oldVideo.getSceneGroupId();
        String seqRange = oldVideo.getStoryboardSeqRange();
        // 从 seqRange("N-N") 解析出目标 seq
        Integer targetSeq = parseSeqStartFromRange(seqRange);

        // 2. 删除旧视频（仅该单条）
        sceneVideoService.removeById(videoId);
        log.info("[单条重生成] 已删除旧场景视频，videoId={}, sceneGroupId={}, seqRange={}, taskId={}",
                videoId, targetGroupId, seqRange, taskId);

        try {
            taskStateManager.markAsRunning(taskId, targetStep.getOrder(), LocalDateTime.now());

            WorkflowTaskInfo taskInfo = taskInfoProvider.getTaskInfo(taskId);
            if (taskInfo == null) throw new BizException("任务不存在：taskId=" + taskId);

            StepContext context = buildStepContext(taskInfo);
            if (overrides != null && !overrides.isEmpty()) {
                context.setOverrides(overrides);
            }
            artifactLoader.loadArtifacts(context, taskId, targetStep.getOrder());

            // 3. 找到 VideoStepHandler，通过 getBatchItems 复用"组内分镜链"构建逻辑（保证与批量路径一致）
            AbstractStepHandler rawHandler = stepHandlers.stream()
                    .filter(h -> h.getStep() == targetStep)
                    .findFirst()
                    .orElseThrow(() -> new BizException("找不到步骤处理器：" + targetStep.getName()));
            if (!(rawHandler instanceof com.comicdrama.workflow.handler.VideoStepHandler)) {
                throw new BizException("步骤处理器类型错误，期望 VideoStepHandler");
            }
            com.comicdrama.workflow.handler.VideoStepHandler videoHandler =
                    (com.comicdrama.workflow.handler.VideoStepHandler) rawHandler;

            // 应用覆盖参数（在 getBatchItems 之后已构建好 item 也不影响，因为下面改的是 context.requestDTO 和 artifact 中 Storyboard 的引用）
            if (overrides != null && !overrides.isEmpty()) {
                TaskCreateDTO dto = context.getRequestDTO();
                if (dto != null) {
                    if (overrides.containsKey("artStyle")) {
                        dto.setArtStyle((String) overrides.get("artStyle"));
                    }
                    if (overrides.containsKey("visualStyle")) {
                        dto.setVisualStyle((String) overrides.get("visualStyle"));
                    }
                }
                // duration 覆盖：修改目标 Storyboard 的 duration（会被 processBatchItem 内 buildAgnesRequest 用到）
                if (overrides.containsKey("duration") && targetSeq != null) {
                    try {
                        int targetDuration = Integer.parseInt(String.valueOf(overrides.get("duration")));
                        java.util.List<com.comicdrama.workflow.entity.Storyboard> storyboards =
                                context.getArtifact(StepEnum.STORYBOARD);
                        if (storyboards != null) {
                            for (com.comicdrama.workflow.entity.Storyboard sb : storyboards) {
                                if (targetSeq.equals(sb.getSeq())) {
                                    sb.setDuration(targetDuration);
                                    log.info("[单条重生成] 已覆盖目标分镜 seq={} duration={}", targetSeq, targetDuration);
                                    break;
                                }
                            }
                        }
                    } catch (Exception ignore) {
                        log.warn("[单条重生成] overrides.duration 解析失败：{}", overrides.get("duration"));
                    }
                }
                log.info("[单条重生成] 已应用覆盖参数: {}", overrides);
            }

            // 4. 调用 getBatchItemsPublic 构建所有分镜 batchItem（与批量生成路径完全一致的 isFirstInGroup / prevImageUrl 链）
            java.util.List<Object> allItems;
            {
                java.util.List<Object> gen = (java.util.List<Object>) videoHandler.getBatchItemsPublic(context);
                allItems = gen != null ? gen : new java.util.ArrayList<>();
            }

            // 5. 找到目标 item：groupId 和 seq 都匹配
            Object targetItem = null;
            int targetIndex = -1;
            for (int i = 0; i < allItems.size(); i++) {
                Object it = allItems.get(i);
                if (!(it instanceof com.comicdrama.workflow.handler.VideoStepHandler.StoryboardBatchItem)) continue;
                com.comicdrama.workflow.handler.VideoStepHandler.StoryboardBatchItem sbItem =
                        (com.comicdrama.workflow.handler.VideoStepHandler.StoryboardBatchItem) it;
                boolean groupMatch = targetGroupId == null
                        ? (sbItem.groupId == null)
                        : targetGroupId.equals(sbItem.groupId);
                boolean seqMatch = (targetSeq == null)
                        || (sbItem.sb != null && targetSeq.equals(sbItem.sb.getSeq()));
                // storyboardIds 匹配：防止 groupId 相同但对应旧视频的 Storyboard 已被替换
                boolean sbIdMatch = true;
                if (oldVideo.getStoryboardIds() != null && sbItem.sb != null && sbItem.sb.getId() != null) {
                    String expected = String.valueOf(sbItem.sb.getId());
                    sbIdMatch = oldVideo.getStoryboardIds().contains(expected);
                }
                if (groupMatch && seqMatch && sbIdMatch) {
                    targetItem = it;
                    targetIndex = i;
                    break;
                }
            }
            if (targetItem == null) {
                throw new BizException("无法定位该视频对应的分镜 item（sceneGroupId=" + targetGroupId
                        + ", seqRange=" + seqRange + "），无法重生成");
            }

            nodeStateManager.saveNodeState(taskId, targetStep.getOrder(), 1, LocalDateTime.now(),
                    null, null, null, null);

            java.time.LocalDateTime stepStart = LocalDateTime.now();
            long stepStartMs = System.currentTimeMillis();

            // 6. 单个分镜调用 AI 生成视频
            Object result = videoHandler.processBatchItem(targetItem, targetIndex, context);
            if (result == null) {
                throw new BizException(opName + "失败：AI 返回空结果，请稍后重试");
            }
            videoHandler.saveBatchResult(targetItem, result, context);

            long durationMs = System.currentTimeMillis() - stepStartMs;
            nodeStateManager.saveNodeState(taskId, targetStep.getOrder(), 2, stepStart,
                    LocalDateTime.now(), durationMs, null, null);

            int totalProgress = calculateTotalProgress(targetStep.getOrder(), 100);
            taskStateManager.updateStepProgress(taskId, targetStep.getOrder(), 100, totalProgress);

            broadcaster.publish(com.comicdrama.common.constant.CacheConstants.CHANNEL_TASK_PROGRESS,
                    new TaskProgressEvent(this, taskId, targetStep.getOrder(), targetStep.getCode(),
                            100, totalProgress, "单条场景视频已重生成"));

            markBackToIdleState(taskId, curStatus, targetStep.getOrder(), totalProgress);

            log.info("========== {}完成 taskId={}, videoId={}, seq={} ==========",
                    opName, taskId, videoId, targetSeq);
        } catch (BizException e) {
            log.warn("{}业务异常 taskId={}, videoId={}: {}", opName, taskId, videoId, e.getMessage());
            try { markBackToIdleState(taskId, curStatus, targetStep.getOrder(), null); } catch (Exception ignore) {}
            try { failureReporter.reportFailure(taskId, targetStep.getOrder(), opName + "失败", e.getMessage(), e); } catch (Exception ignore) {}
            throw e;
        } catch (Exception e) {
            log.error("{}失败 taskId={}, videoId={}", opName, taskId, videoId, e);
            try { markBackToIdleState(taskId, curStatus, targetStep.getOrder(), null); } catch (Exception ignore) {}
            try { failureReporter.reportFailure(taskId, targetStep.getOrder(), opName + "失败", e.getMessage(), e); } catch (Exception ignore) {}
            throw new BizException(opName + "失败：" + e.getMessage());
        }
    }

    /**
     * 从 scene_video.storyboard_seq_range 解析出首个 seq。
     * 新格式："N-N"（单分镜）→ 返回 N；旧格式兼容："M-N"（多帧合并，兼容兜底）→ 返回 M。
     */
    private Integer parseSeqStartFromRange(String seqRange) {
        if (seqRange == null || seqRange.isEmpty()) return null;
        try {
            int hyphen = seqRange.indexOf('-');
            String first = (hyphen >= 0) ? seqRange.substring(0, hyphen) : seqRange;
            return Integer.parseInt(first.trim());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 单张重生成结束后，把任务回到"空闲可再次操作"的状态：
     * - 原失败则保持失败（方便用户继续断点续跑/排查）
     * - 原完成则保持完成（只是单条微调，不打断已完成节奏）
     * - 原暂停则回到暂停（默认规则：单步微调后仍等待用户再次确认下一步）
     *
     * 额外：不管什么状态都广播一次 4（暂停）事件，前端立即刷新详情。
     */
    private void markBackToIdleState(Long taskId, int originalStatus, Integer stepOrder, Integer totalProgress) {
        int target;
        String targetLabel;
        if (originalStatus == 2) {
            target = 2; targetLabel = "已完成";
        } else {
            target = 4; targetLabel = "已暂停";
        }

        // 更新任务状态
        if (target == 4) {
            taskStateManager.markAsPaused(taskId, stepOrder == null ? 0 : stepOrder, LocalDateTime.now());
        } else if (target == 2) {
            if (totalProgress != null) {
                taskStateManager.updateStepProgress(taskId, 9, 100, totalProgress);
            }
        }

        broadcaster.publish(com.comicdrama.common.constant.CacheConstants.CHANNEL_TASK_STATUS,
                new TaskStatusChangeEvent(this, taskId, 1, target));
        log.info("[单张重生成] 任务状态已回到 taskId={}, targetStatus={}({})", taskId, target, targetLabel);
    }

    /**
     * 构建步骤执行上下文。
     */
    private StepContext buildStepContext(WorkflowTaskInfo taskInfo) {
        return StepContext.builder()
                .taskId(taskInfo.taskId())
                .taskNo(taskInfo.taskNo())
                .userId(taskInfo.userId())
                .requestDTO(taskInfo.requestDTO())
                .progress(0)
                .totalProgress(0)
                .completedSteps(new java.util.HashMap<>())
                .build();
    }

    /**
     * 获取按 order 排序的步骤处理器列表。
     * 过滤掉已弃用的处理器（getStep() 返回 null）。
     */
    private List<AbstractStepHandler> getSortedHandlers() {
        return stepHandlers.stream()
                .filter(h -> h.getStep() != null)
                .sorted(Comparator.comparingInt(h -> h.getStep().getOrder()))
                .collect(Collectors.toList());
    }

    /**
     * 计算流水线总进度（基于 StepEnum 步骤数均等权重 + 当前步骤内进度）。
     */
    private int calculateTotalProgress(int stepOrder, int stepProgress) {
        int baseProgress = (stepOrder - 1) * (100 / StepEnum.values().length);
        int stepContribution = (stepProgress * (100 / StepEnum.values().length)) / 100;
        return Math.min(baseProgress + stepContribution, 100);
    }

    /**
     * 判断指定步骤是否应被跳过。
     * 当前规则：AUDIO（步骤7）在 voiceEnabled=0 时跳过。
     */
    private boolean shouldSkipStep(StepEnum step, TaskCreateDTO requestDTO) {
        if (step == StepEnum.AUDIO && requestDTO != null) {
            Integer voiceEnabled = requestDTO.getVoiceEnabled();
            if (voiceEnabled != null && voiceEnabled == 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取异常堆栈字符串。
     */
    private String getStackTraceString(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(throwable.getClass().getName()).append(": ").append(throwable.getMessage()).append("\n");
        for (StackTraceElement element : throwable.getStackTrace()) {
            sb.append("\tat ").append(element.toString()).append("\n");
        }
        return sb.toString();
    }

    /**
     * 解析失败步骤编号。
     * 支持从错误消息中提取步骤信息，匹配优先级：
     * 1. 从堆栈中查找 StepHandler 相关类（最可靠，定位实际抛异常的Handler）
     * 2. 从 [STEP_CODE] 格式提取（如 [asset_image]）
     * 3. 匹配步骤中文名称（如 "分镜绘图"）
     * 4. 默认返回 startStep
     */
    private int resolveFailureStep(Exception e, int defaultStep) {
        String msg = e.getMessage();

        // 策略1: 从堆栈中查找 StepHandler 相关类（最可靠）
        // 堆栈直接指向抛异常的Handler类，避免被错误消息中的依赖步骤码干扰
        Integer stackStep = resolveStepFromStackTrace(e);
        if (stackStep != null) {
            // 策略2: 同时检查消息中的[STEP_CODE]，如果两者一致则增强置信度
            Integer msgStep = resolveStepFromMessage(msg);
            if (msgStep != null && msgStep.equals(stackStep)) {
                log.debug("堆栈与消息一致，确认失败步骤：order={}", stackStep);
            } else if (msgStep != null) {
                log.debug("堆栈步骤({})与消息步骤({})不一致，以堆栈为准", stackStep, msgStep);
            }
            return stackStep;
        }

        // 策略2: 从 [STEP_CODE] 格式提取（忽略大小写）
        Integer msgStep = resolveStepFromMessage(msg);
        if (msgStep != null) {
            return msgStep;
        }

        // 策略3: 匹配步骤中文名称
        if (msg != null) {
            for (StepEnum s : StepEnum.values()) {
                if (msg.contains(s.getName())) {
                    log.debug("从消息中提取失败步骤（中文名匹配）：name={}, order={}", s.getName(), s.getOrder());
                    return s.getOrder();
                }
            }
        }

        // 策略4: 默认返回起始步骤
        log.warn("无法确定失败步骤，使用默认值：startStep={}", defaultStep);
        return defaultStep;
    }

    /**
     * 从堆栈中提取失败步骤（查找 StepHandler 类名）。
     */
    private Integer resolveStepFromStackTrace(Exception e) {
        for (StackTraceElement element : e.getStackTrace()) {
            String className = element.getClassName();
            for (StepEnum s : StepEnum.values()) {
                String handlerName = toPascalCase(s.name()) + "StepHandler";
                if (className.contains(handlerName)) {
                    log.debug("从堆栈中提取失败步骤：handler={}, order={}", handlerName, s.getOrder());
                    return s.getOrder();
                }
            }
        }
        return null;
    }

    /**
     * 从错误消息中提取 [STEP_CODE] 格式的失败步骤。
     */
    private Integer resolveStepFromMessage(String msg) {
        if (msg == null) return null;
        String upperMsg = msg.toUpperCase();
        for (StepEnum s : StepEnum.values()) {
            if (upperMsg.contains("[" + s.getCode().toUpperCase() + "]")) {
                log.debug("从消息中提取失败步骤：code={}, order={}", s.getCode(), s.getOrder());
                return s.getOrder();
            }
        }
        return null;
    }

    /**
     * 将枚举名从 UPPER_SNAKE_CASE 转换为 PascalCase。
     * 例如：VIDEO → Video, ASSET_DERIVE → AssetDerive, STORYBOARD_IMAGE → StoryboardImage
     */
    private String toPascalCase(String upperSnake) {
        StringBuilder sb = new StringBuilder();
        boolean nextUpper = true;
        for (char c : upperSnake.toCharArray()) {
            if (c == '_') {
                nextUpper = true;
            } else if (nextUpper) {
                sb.append(Character.toUpperCase(c));
                nextUpper = false;
            } else {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    /**
     * 解析失败步骤编码。
     */
    private String resolveFailureStepCode(Exception e, int failureStep) {
        StepEnum step = StepEnum.of(failureStep);
        if (step != null) {
            return step.getCode();
        }
        return "unknown";
    }

    /**
     * 验证模型配置完整性。
     * 检查所有必填步骤是否配置了有效的 AI 模型。
     * 配音步骤（AUDIO）在 voiceEnabled=0 时跳过验证。
     */
    private void validateModelConfigurations(WorkflowTaskInfo taskInfo) {
        TaskCreateDTO requestDTO = taskInfo.requestDTO();
        boolean skipAudio = requestDTO != null &&
                requestDTO.getVoiceEnabled() != null &&
                requestDTO.getVoiceEnabled() == 0;

        List<StepEnum> missingSteps = bindingResolver.validateRequiredBindings(skipAudio);
        if (!missingSteps.isEmpty()) {
            StringBuilder sb = new StringBuilder("AI模型配置缺失：");
            for (int i = 0; i < missingSteps.size(); i++) {
                if (i > 0) sb.append("、");
                sb.append(missingSteps.get(i).getName());
            }
            sb.append("。请前往「系统设置 → 模型配置」页面补充配置");
            log.error(sb.toString());
            throw new BizException(sb.toString());
        }
    }
}
