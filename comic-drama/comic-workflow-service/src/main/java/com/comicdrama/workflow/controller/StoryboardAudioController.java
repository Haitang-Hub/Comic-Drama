package com.comicdrama.workflow.controller;

import com.comicdrama.workflow.entity.StoryboardAudio;
import com.comicdrama.workflow.service.StoryboardAudioService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 分镜配音（步骤6产物：Seed-TTS） */
@RestController
@RequestMapping("/api/workflow/audio")
@RequiredArgsConstructor
public class StoryboardAudioController extends AbstractWorkflowController<StoryboardAudioService, StoryboardAudio> {

    private final StoryboardAudioService storyboardAudioService;

    @Override
    protected StoryboardAudioService getService() {
        return storyboardAudioService;
    }
}
