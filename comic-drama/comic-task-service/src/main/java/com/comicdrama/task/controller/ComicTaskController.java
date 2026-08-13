package com.comicdrama.task.controller;

import com.comicdrama.common.dto.PageQuery;
import com.comicdrama.common.dto.TaskCreateDTO;
import com.comicdrama.common.result.PageResult;
import com.comicdrama.common.result.Result;
import com.comicdrama.task.client.WorkflowClient;
import com.comicdrama.task.entity.ComicTask;
import com.comicdrama.task.entity.TaskNodeState;
import com.comicdrama.task.entity.TaskProgressLog;
import com.comicdrama.task.service.TaskNodeStateService;
import com.comicdrama.task.service.TaskProgressLogService;
import com.comicdrama.task.service.TaskService;
import com.comicdrama.task.vo.TaskDetailVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 漫剧任务（Phase-1 端到端核心 + Phase-3 节点重生成/断点续跑）
 */
@Slf4j
@RestController
@RequestMapping("/api/task")
@RequiredArgsConstructor
public class ComicTaskController {

    private final TaskService taskService;
    private final TaskProgressLogService taskProgressLogService;
    private final TaskNodeStateService taskNodeStateService;
    private final WorkflowClient workflowClient;

    @PostMapping
    public Result<ComicTask> create(@RequestBody @Valid TaskCreateDTO dto) {
        return Result.ok(taskService.createTask(dto));
    }

    @GetMapping("/page")
    public Result<PageResult<ComicTask>> page(PageQuery query,
                                                @RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) Integer status,
                                                @RequestParam(required = false, defaultValue = "false") boolean queryAll) {
        return Result.ok(taskService.page(query, keyword, status, queryAll));
    }

    @GetMapping("/{id}")
    public Result<TaskDetailVO> get(@PathVariable Long id) {
        return Result.ok(taskService.getDetail(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        taskService.removeById(id);
        return Result.ok();
    }

    @PutMapping("/{id}/pause")
    public Result<Void> pause(@PathVariable Long id,
                              @RequestParam(required = false, defaultValue = "false") boolean rollbackCurrentStep,
                              @RequestParam(required = false, defaultValue = "false") boolean stopAfterCurrentStep) {
        taskService.pause(id, rollbackCurrentStep, stopAfterCurrentStep);
        return Result.ok();
    }

    @PutMapping("/{id}/resume")
    public Result<Void> resume(@PathVariable Long id) {
        taskService.resume(id);
        return Result.ok();
    }

    /**
     * 执行下一步骤（语义=继续按钮：从暂停状态开始，执行单一的下一个步骤，完成后再次暂停）。
     * 与 approve 不同：此接口不限制 execMode，任何暂停态任务都能使用，单步执行。
     */
    @PostMapping("/{id}/next-step")
    public Result<Void> executeNextStep(@PathVariable Long id) {
        taskService.executeNextStep(id);
        return Result.ok();
    }

    @PutMapping("/{id}/retry")
    public Result<Void> retry(@PathVariable Long id) {
        taskService.retry(id);
        return Result.ok();
    }

    @PostMapping("/{id}/regenerate")
    public Result<Void> regenerateNode(@PathVariable Long id,
                                        @RequestParam Integer stepOrder,
                                        @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> overrides = body != null ? body : new HashMap<>();
        log.info("收到节点重生成请求 taskId={}, stepOrder={}, overrides={}", id, stepOrder, overrides);
        try {
            ComicTask task = taskService.getById(id);
            Map<String, Object> request = new HashMap<>();
            request.put("taskId", id);
            request.put("userId", 1L);
            request.put("overrides", overrides);
            if (task != null) {
                Map<String, Object> dtoMap = new HashMap<>();
                dtoMap.put("title", task.getTitle());
                dtoMap.put("storyRequirement", task.getStoryRequirement());
                dtoMap.put("duration", task.getDuration());
                dtoMap.put("aspectRatio", task.getAspectRatio());
                dtoMap.put("resolution", task.getResolution());
                dtoMap.put("voiceEnabled", task.getVoiceEnabled());
                dtoMap.put("execMode", task.getExecMode());
                dtoMap.put("artStyle", task.getArtStyle());
                dtoMap.put("visualStyle", task.getVisualStyle());
                request.put("taskCreateDTO", dtoMap);
            }
            workflowClient.regenerate(request, stepOrder);
            return Result.ok();
        } catch (Exception e) {
            log.error("节点重生成失败 taskId={}", id, e);
            return Result.fail("节点重生成请求失败：" + e.getMessage());
        }
    }

    /**
     * 单张资产图重生成（步骤4 首版资产图 / 步骤5 衍生资产图 通用，按 imageId 自动判定归属）。
     */
    @PostMapping("/{id}/regenerate/asset-image/{imageId}")
    public Result<Void> regenerateAssetImage(@PathVariable Long id,
                                             @PathVariable Long imageId,
                                             @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> overrides = body != null ? body : new HashMap<>();
        log.info("收到单张资产图重生成请求 taskId={}, imageId={}, overrides={}", id, imageId, overrides);
        try {
            ComicTask task = taskService.getById(id);
            Map<String, Object> request = new HashMap<>();
            request.put("taskId", id);
            request.put("userId", 1L);
            request.put("overrides", overrides);
            if (task != null) {
                Map<String, Object> dtoMap = new HashMap<>();
                dtoMap.put("title", task.getTitle());
                dtoMap.put("storyRequirement", task.getStoryRequirement());
                dtoMap.put("duration", task.getDuration());
                dtoMap.put("aspectRatio", task.getAspectRatio());
                dtoMap.put("resolution", task.getResolution());
                dtoMap.put("voiceEnabled", task.getVoiceEnabled());
                dtoMap.put("execMode", task.getExecMode());
                dtoMap.put("artStyle", task.getArtStyle());
                dtoMap.put("visualStyle", task.getVisualStyle());
                request.put("taskCreateDTO", dtoMap);
            }
            workflowClient.regenerateAssetImage(request, imageId);
            return Result.ok();
        } catch (Exception e) {
            log.error("单张资产图重生成失败 taskId={}, imageId={}", id, imageId, e);
            return Result.fail("单张资产图重生成请求失败：" + e.getMessage());
        }
    }

    /**
     * 单张分镜图重生成（步骤6）。
     */
    @PostMapping("/{id}/regenerate/storyboard-image/{imageId}")
    public Result<Void> regenerateStoryboardImage(@PathVariable Long id,
                                                  @PathVariable Long imageId,
                                                  @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> overrides = body != null ? body : new HashMap<>();
        log.info("收到单张分镜图重生成请求 taskId={}, imageId={}, overrides={}", id, imageId, overrides);
        try {
            ComicTask task = taskService.getById(id);
            Map<String, Object> request = new HashMap<>();
            request.put("taskId", id);
            request.put("userId", 1L);
            request.put("overrides", overrides);
            if (task != null) {
                Map<String, Object> dtoMap = new HashMap<>();
                dtoMap.put("title", task.getTitle());
                dtoMap.put("storyRequirement", task.getStoryRequirement());
                dtoMap.put("duration", task.getDuration());
                dtoMap.put("aspectRatio", task.getAspectRatio());
                dtoMap.put("resolution", task.getResolution());
                dtoMap.put("voiceEnabled", task.getVoiceEnabled());
                dtoMap.put("execMode", task.getExecMode());
                dtoMap.put("artStyle", task.getArtStyle());
                dtoMap.put("visualStyle", task.getVisualStyle());
                request.put("taskCreateDTO", dtoMap);
            }
            workflowClient.regenerateStoryboardImage(request, imageId);
            return Result.ok();
        } catch (Exception e) {
            log.error("单张分镜图重生成失败 taskId={}, imageId={}", id, imageId, e);
            return Result.fail("单张分镜图重生成请求失败：" + e.getMessage());
        }
    }

    /**
     * 单条场景视频重生成（步骤8）。
     */
    @PostMapping("/{id}/regenerate/scene-video/{videoId}")
    public Result<Void> regenerateSceneVideo(@PathVariable Long id,
                                             @PathVariable Long videoId,
                                             @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> overrides = body != null ? body : new HashMap<>();
        log.info("收到单条场景视频重生成请求 taskId={}, videoId={}, overrides={}", id, videoId, overrides);
        try {
            ComicTask task = taskService.getById(id);
            Map<String, Object> request = new HashMap<>();
            request.put("taskId", id);
            request.put("userId", 1L);
            request.put("overrides", overrides);
            if (task != null) {
                Map<String, Object> dtoMap = new HashMap<>();
                dtoMap.put("title", task.getTitle());
                dtoMap.put("storyRequirement", task.getStoryRequirement());
                dtoMap.put("duration", task.getDuration());
                dtoMap.put("aspectRatio", task.getAspectRatio());
                dtoMap.put("resolution", task.getResolution());
                dtoMap.put("voiceEnabled", task.getVoiceEnabled());
                dtoMap.put("execMode", task.getExecMode());
                dtoMap.put("artStyle", task.getArtStyle());
                dtoMap.put("visualStyle", task.getVisualStyle());
                request.put("taskCreateDTO", dtoMap);
            }
            workflowClient.regenerateSceneVideo(request, videoId);
            return Result.ok();
        } catch (Exception e) {
            log.error("单条场景视频重生成失败 taskId={}, videoId={}", id, videoId, e);
            return Result.fail("单条场景视频重生成请求失败：" + e.getMessage());
        }
    }

    @PostMapping("/{id}/resume-from-failure")
    public Result<Void> resumeFromFailure(@PathVariable Long id) {
        log.info("收到断点续跑请求 taskId={}", id);
        try {
            // 查询任务的节点状态，找到最近失败的步骤
            List<TaskNodeState> nodes = taskNodeStateService.listByTaskId(id);
            int failedStep = 1;
            for (TaskNodeState node : nodes) {
                // nodeStatus: 0等待 1进行中 2成功 3失败
                if (node.getNodeStatus() != null && node.getNodeStatus() == 3 && node.getStep() != null) {
                    failedStep = node.getStep();
                    break;
                }
            }
            Map<String, Object> request = new HashMap<>();
            request.put("taskId", id);
            request.put("userId", 1L);
            request.put("maxSteps", 9);
            workflowClient.resume(request, failedStep);
            return Result.ok();
        } catch (Exception e) {
            log.error("断点续跑失败 taskId={}", id, e);
            return Result.fail("断点续跑请求失败：" + e.getMessage());
        }
    }

    @PostMapping("/{id}/resume-from-step")
    public Result<Void> resumeFromStep(@PathVariable Long id,
                                        @RequestParam Integer stepOrder) {
        log.info("收到从步骤续跑请求 taskId={}, stepOrder={}", id, stepOrder);
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("taskId", id);
            request.put("userId", 1L);
            request.put("maxSteps", 9);
            workflowClient.resume(request, stepOrder);
            return Result.ok();
        } catch (Exception e) {
            log.error("从步骤续跑失败 taskId={}", id, e);
            return Result.fail("从步骤续跑请求失败：" + e.getMessage());
        }
    }

    @PostMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id) {
        taskService.approve(id);
        return Result.ok();
    }

    @GetMapping("/{id}/progress")
    public Result<List<TaskProgressLog>> getProgress(@PathVariable Long id) {
        return Result.ok(taskProgressLogService.listByTaskId(id));
    }

    @GetMapping("/{id}/nodes")
    public Result<List<TaskNodeState>> getNodes(@PathVariable Long id) {
        return Result.ok(taskNodeStateService.listByTaskId(id));
    }

    /**
     * 获取任务的成片播放 manifest.json。
     * 1) 优先从 comic_task.final_work_manifest 读取（VideoMergeStepHandler 生成的最终版）
     * 2) 若为空则从 scene_video 表实时构建并回写 DB（兼容历史任务）
     * 返回 JSON 字符串（含 videos 数组，前端直接解析即可在线播放）。
     */
    @GetMapping(value = "/{id}/manifest", produces = "application/json; charset=utf-8")
    public Result<String> getManifest(@PathVariable Long id) {
        return Result.ok(taskService.getOrBuildFinalWorkManifest(id));
    }
}
