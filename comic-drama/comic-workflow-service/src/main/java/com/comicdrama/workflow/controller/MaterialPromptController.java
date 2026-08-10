package com.comicdrama.workflow.controller;

import com.comicdrama.workflow.entity.MaterialPrompt;
import com.comicdrama.workflow.service.MaterialPromptService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 素材提示词（步骤4产物） */
@RestController
@RequestMapping("/api/workflow/material")
@RequiredArgsConstructor
public class MaterialPromptController extends AbstractWorkflowController<MaterialPromptService, MaterialPrompt> {

    private final MaterialPromptService materialPromptService;

    @Override
    protected MaterialPromptService getService() {
        return materialPromptService;
    }
}
