package com.comicdrama.workflow.controller;

import com.comicdrama.workflow.entity.StoryboardImage;
import com.comicdrama.workflow.service.StoryboardImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 分镜画面（步骤5产物：Seedream） */
@RestController
@RequestMapping("/api/workflow/image")
@RequiredArgsConstructor
public class StoryboardImageController extends AbstractWorkflowController<StoryboardImageService, StoryboardImage> {

    private final StoryboardImageService storyboardImageService;

    @Override
    protected StoryboardImageService getService() {
        return storyboardImageService;
    }
}
