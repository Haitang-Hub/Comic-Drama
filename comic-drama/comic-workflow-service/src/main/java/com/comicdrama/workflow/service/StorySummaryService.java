package com.comicdrama.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.comicdrama.workflow.entity.StorySummary;
import com.comicdrama.workflow.mapper.StorySummaryMapper;
import org.springframework.stereotype.Service;

@Service
public class StorySummaryService extends AbstractWorkflowService<StorySummaryMapper, StorySummary> {

    /**
     * 更新故事摘要。
     * @param taskId      任务ID
     * @param outlineText 大纲正文（存入 content 字段）
     * @param summary     摘要（当前与 outlineText 合并存储，若为空则忽略）
     */
    public void updateSummary(Long taskId, String outlineText, String summary) {
        StorySummary entity = this.getOne(new QueryWrapper<StorySummary>().eq("task_id", taskId));
        if (entity == null) {
            throw new RuntimeException("故事摘要不存在 taskId=" + taskId);
        }
        if (outlineText != null) {
            entity.setContent(outlineText);
        }
        this.updateById(entity);
    }
}
