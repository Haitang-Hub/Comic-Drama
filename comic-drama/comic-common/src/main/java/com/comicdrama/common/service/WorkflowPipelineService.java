package com.comicdrama.common.service;

import com.comicdrama.common.dto.NodeStateSnapshot;
import com.comicdrama.common.dto.TaskCreateDTO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 工作流流水线服务接口（跨模块共享）。
 * 负责编排 7 步流水线（大纲 → 场景分组 → 分镜 → 素材 → 画面 → 音频 → 视频）的执行。
 *
 * <p>使用模板方法模式，由 {@code AbstractStepHandler} 实现各步骤的具体逻辑。
 * 通过回调接口与外部服务（task-service、resource-service）进行交互，实现解耦。</p>
 *
 * <h3>回调接口说明</h3>
 * <ul>
 *   <li>{@link TaskStateManager} - 管理任务状态（排队→生成中→已完成/失败）</li>
 *   <li>{@link ProgressReporter} - 记录步骤进度</li>
 *   <li>{@link FailureReporter} - 记录失败日志</li>
 *   <li>{@link WorkCreator} - 创建 ComicWork 作品记录</li>
 *   <li>{@link TaskInfoProvider} - 获取任务信息</li>
 *   <li>{@link NodeStateManager} - 管理节点状态（Phase-3）</li>
 * </ul>
 */
public interface WorkflowPipelineService {

    /**
     * 执行完整 9 步流水线（从第 1 步开始）。
     *
     * @param taskId 任务 ID
     */
    default void executePipeline(Long taskId) {
        executeFromStep(taskId, 1);
    }

    /**
     * 执行流水线（从第 1 步开始，最多执行 maxStep 步）。
     *
     * @param taskId  任务 ID
     * @param maxStep 最大执行步骤数（1-9），用于仅执行文本阶段
     */
    default void executePipeline(Long taskId, int maxStep) {
        executeFromStep(taskId, 1, maxStep);
    }

    /**
     * 从指定步骤开始执行流水线（执行至第 9 步）。
     *
     * @param taskId    任务 ID
     * @param startStep 起始步骤顺序（1-9）
     */
    default void executeFromStep(Long taskId, int startStep) {
        executeFromStep(taskId, startStep, 9);
    }

    /**
     * 从指定步骤开始执行流水线，最多执行 maxStep 步。
     *
     * @param taskId    任务 ID
     * @param startStep 起始步骤顺序（1-9）
     * @param maxStep   最大执行步骤数（1-9）
     */
    void executeFromStep(Long taskId, int startStep, int maxStep);

    /**
     * 节点重生成：重新执行指定步骤及其下游（Phase-3）。
     *
     * @param taskId    任务 ID
     * @param stepOrder 目标步骤顺序（1-9）
     */
    default void regenerateNode(Long taskId, int stepOrder) {
        regenerateNode(taskId, stepOrder, null);
    }

    /**
     * 节点重生成（带参数覆盖）：重新执行指定步骤及其下游。
     *
     * @param taskId    任务 ID
     * @param stepOrder 目标步骤顺序（1-9）
     * @param overrides 参数覆盖（如 artStyle、visualStyle）
     */
    void regenerateNode(Long taskId, int stepOrder, Map<String, Object> overrides);

    /**
     * 单张资产图重生成（步骤4 首版资产图 / 步骤5 衍生资产图共用，按 imageId 自动判断归属）。
     *
     * @param taskId   任务 ID
     * @param imageId  资产图主键 ID（asset_image 表 id）
     */
    default void regenerateAssetImage(Long taskId, Long imageId) {
        regenerateAssetImage(taskId, imageId, null);
    }

    /**
     * 单张资产图重生成（带参数覆盖）。
     *
     * @param taskId    任务 ID
     * @param imageId   资产图主键 ID
     * @param overrides 参数覆盖（如 assetDesc、artStyle、visualStyle）
     */
    void regenerateAssetImage(Long taskId, Long imageId, Map<String, Object> overrides);

    /**
     * 单张分镜图重生成（步骤6）。
     *
     * @param taskId    任务 ID
     * @param imageId   分镜图主键 ID（storyboard_image 表 id）
     */
    default void regenerateStoryboardImage(Long taskId, Long imageId) {
        regenerateStoryboardImage(taskId, imageId, null);
    }

    /**
     * 单张分镜图重生成（带参数覆盖）。
     *
     * @param taskId    任务 ID
     * @param imageId   分镜图主键 ID
     * @param overrides 参数覆盖（如 visualDesc、artStyle、visualStyle）
     */
    void regenerateStoryboardImage(Long taskId, Long imageId, Map<String, Object> overrides);

    /**
     * 单条场景视频重生成（步骤8）。
     *
     * @param taskId   任务 ID
     * @param videoId  场景视频主键 ID（scene_video 表 id）
     */
    default void regenerateSceneVideo(Long taskId, Long videoId) {
        regenerateSceneVideo(taskId, videoId, null);
    }

    /**
     * 单条场景视频重生成（带参数覆盖）。
     *
     * @param taskId    任务 ID
     * @param videoId   场景视频主键 ID
     * @param overrides 参数覆盖（如 duration、prompt、artStyle、visualStyle 等）
     */
    void regenerateSceneVideo(Long taskId, Long videoId, Map<String, Object> overrides);

    /**
     * 断点续跑：从最近失败步骤开始继续执行（Phase-3）。
     *
     * @param taskId 任务 ID
     */
    void resumeFromFailure(Long taskId);
}