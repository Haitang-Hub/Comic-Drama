package com.comicdrama.workflow.service;

import com.comicdrama.workflow.entity.SceneVideo;
import com.comicdrama.workflow.mapper.SceneVideoMapper;
import org.springframework.stereotype.Service;

@Service
public class SceneVideoService extends AbstractWorkflowService<SceneVideoMapper, SceneVideo> {

    /** 替换视频 URL */
    public void replaceVideo(Long taskId, Long videoId, String newVideoUrl, String newCoverUrl) {
        SceneVideo entity = this.getById(videoId);
        if (entity == null) {
            throw new RuntimeException("视频不存在 id=" + videoId);
        }
        entity.setVideoUrl(newVideoUrl);
        if (newCoverUrl != null) {
            entity.setThumbnailUrl(newCoverUrl);
        }
        this.updateById(entity);
    }
}
