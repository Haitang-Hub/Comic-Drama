package com.comicdrama.workflow.service;

import com.comicdrama.workflow.entity.StoryboardAudio;
import com.comicdrama.workflow.mapper.StoryboardAudioMapper;
import org.springframework.stereotype.Service;

@Service
public class StoryboardAudioService extends AbstractWorkflowService<StoryboardAudioMapper, StoryboardAudio> {

    /** 替换音频 URL */
    public void replaceAudio(Long taskId, Long audioId, String newAudioUrl) {
        StoryboardAudio entity = this.getById(audioId);
        if (entity == null) {
            throw new RuntimeException("音频不存在 id=" + audioId);
        }
        entity.setAudioUrl(newAudioUrl);
        this.updateById(entity);
    }
}
