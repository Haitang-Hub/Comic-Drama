package com.comicdrama.workflow.service;

import com.comicdrama.workflow.entity.AssetImage;
import com.comicdrama.workflow.mapper.AssetImageMapper;
import org.springframework.stereotype.Service;

@Service
public class AssetImageService extends AbstractWorkflowService<AssetImageMapper, AssetImage> {

    /** 替换图片 URL */
    public void replaceImage(Long taskId, Long imageId, String newImageUrl, String newThumbnailUrl) {
        AssetImage entity = this.getById(imageId);
        if (entity == null) {
            throw new RuntimeException("资产图片不存在 id=" + imageId);
        }
        entity.setImageUrl(newImageUrl);
        if (newThumbnailUrl != null) {
            entity.setThumbnailUrl(newThumbnailUrl);
        }
        this.updateById(entity);
    }
}
