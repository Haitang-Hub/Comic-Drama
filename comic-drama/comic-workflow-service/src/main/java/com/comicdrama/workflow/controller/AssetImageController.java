package com.comicdrama.workflow.controller;

import com.comicdrama.workflow.entity.AssetImage;
import com.comicdrama.workflow.service.AssetImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workflow/asset-image")
@RequiredArgsConstructor
public class AssetImageController extends AbstractWorkflowController<AssetImageService, AssetImage> {

    private final AssetImageService assetImageService;

    @Override
    protected AssetImageService getService() {
        return assetImageService;
    }
}
