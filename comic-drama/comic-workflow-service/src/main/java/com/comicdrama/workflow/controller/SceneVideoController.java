package com.comicdrama.workflow.controller;

import com.comicdrama.workflow.entity.SceneVideo;
import com.comicdrama.workflow.service.SceneVideoService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 场景视频（步骤7产物：Doubao-Video） */
@RestController
@RequestMapping("/api/workflow/video")
@RequiredArgsConstructor
public class SceneVideoController extends AbstractWorkflowController<SceneVideoService, SceneVideo> {

    private final SceneVideoService sceneVideoService;

    @Override
    protected SceneVideoService getService() {
        return sceneVideoService;
    }
}
