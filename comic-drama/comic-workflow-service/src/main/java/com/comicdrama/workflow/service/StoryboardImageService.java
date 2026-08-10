package com.comicdrama.workflow.service;

import com.comicdrama.workflow.entity.StoryboardImage;
import com.comicdrama.workflow.mapper.StoryboardImageMapper;
import org.springframework.stereotype.Service;

@Service
public class StoryboardImageService extends AbstractWorkflowService<StoryboardImageMapper, StoryboardImage> {

    /** 替换图片 URL */
    public void replaceImage(Long taskId, Long imageId, String newImageUrl, String newThumbnailUrl) {
        StoryboardImage entity = this.getById(imageId);
        if (entity == null) {
            throw new RuntimeException("分镜图片不存在 id=" + imageId);
        }
        entity.setImageUrl(newImageUrl);
        if (newThumbnailUrl != null) {
            entity.setThumbnailUrl(newThumbnailUrl);
        }
        this.updateById(entity);
    }
}
