package com.comicdrama.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.comicdrama.workflow.entity.Storyboard;
import com.comicdrama.workflow.mapper.StoryboardMapper;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class StoryboardService extends AbstractWorkflowService<StoryboardMapper, Storyboard> {

    /** 按 ID 更新分镜脚本字段 */
    public void updateStoryboard(Long taskId, Long storyboardId, Map<String, Object> fields) {
        Storyboard entity = this.getById(storyboardId);
        if (entity == null) {
            throw new RuntimeException("分镜脚本不存在 id=" + storyboardId);
        }
        if (fields != null) {
            if (fields.containsKey("cameraAngle")) entity.setCameraAngle((String) fields.get("cameraAngle"));
            if (fields.containsKey("shotDesc")) entity.setShotDesc((String) fields.get("shotDesc"));
            if (fields.containsKey("scene")) entity.setScene((String) fields.get("scene"));
            if (fields.containsKey("character")) entity.setCharacter((String) fields.get("character"));
            if (fields.containsKey("props")) entity.setProps((String) fields.get("props"));
            if (fields.containsKey("storyboardDesc")) entity.setStoryboardDesc((String) fields.get("storyboardDesc"));
            if (fields.containsKey("dialogue")) entity.setDialogue((String) fields.get("dialogue"));
            if (fields.containsKey("visualDesc")) entity.setVisualDesc((String) fields.get("visualDesc"));
            if (fields.containsKey("duration")) entity.setDuration(((Number) fields.get("duration")).intValue());
        }
        entity.setIsEdited(1);
        this.updateById(entity);
    }
}
