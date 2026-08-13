package com.comicdrama.task.client;

import com.comicdrama.common.dto.TaskCreateDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 工作流服务 Feign 客户端
 * 用于调用 comic-workflow-service 的 API
 */
@FeignClient(name = "comic-workflow-service", path = "/api/workflow/pipeline")
public interface WorkflowClient {

    /**
     * 执行流水线
     */
    @PostMapping("/execute")
    Map<String, Object> execute(@RequestBody Map<String, Object> request);

    /**
     * 从指定步骤继续执行（断点续跑）
     */
    @PostMapping("/resume")
    Map<String, Object> resume(@RequestBody Map<String, Object> request,
                               @RequestParam("startStep") int startStep);

    /**
     * 单步重新生成
     */
    @PostMapping("/regenerate")
    Map<String, Object> regenerate(@RequestBody Map<String, Object> request,
                                  @RequestParam("stepOrder") int stepOrder);

    /**
     * 单张资产图重生成（步骤4 首版/步骤5 衍生共用，按 imageId 自动判定）
     */
    @PostMapping("/regenerate/asset-image")
    Map<String, Object> regenerateAssetImage(@RequestBody Map<String, Object> request,
                                             @RequestParam("imageId") Long imageId);

    /**
     * 单张分镜图重生成（步骤6）
     */
    @PostMapping("/regenerate/storyboard-image")
    Map<String, Object> regenerateStoryboardImage(@RequestBody Map<String, Object> request,
                                                  @RequestParam("imageId") Long imageId);

    /**
     * 单条场景视频重生成（步骤8）
     */
    @PostMapping("/regenerate/scene-video")
    Map<String, Object> regenerateSceneVideo(@RequestBody Map<String, Object> request,
                                             @RequestParam("videoId") Long videoId);
}
