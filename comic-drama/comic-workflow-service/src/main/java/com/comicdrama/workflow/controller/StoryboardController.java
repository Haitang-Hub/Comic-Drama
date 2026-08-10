package com.comicdrama.workflow.controller;

import com.comicdrama.workflow.entity.Storyboard;
import com.comicdrama.workflow.service.StoryboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 分镜脚本（步骤3产物） */
@RestController
@RequestMapping("/api/workflow/storyboard")
@RequiredArgsConstructor
public class StoryboardController extends AbstractWorkflowController<StoryboardService, Storyboard> {

    private final StoryboardService storyboardService;

    @Override
    protected StoryboardService getService() {
        return storyboardService;
    }
}
