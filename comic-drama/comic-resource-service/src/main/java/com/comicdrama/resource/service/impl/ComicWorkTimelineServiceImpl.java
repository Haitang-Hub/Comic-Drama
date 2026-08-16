package com.comicdrama.resource.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.comicdrama.resource.entity.ComicWork;
import com.comicdrama.resource.entity.ComicWorkTimeline;
import com.comicdrama.resource.mapper.ComicWorkTimelineMapper;
import com.comicdrama.resource.service.ComicWorkService;
import com.comicdrama.resource.service.ComicWorkTimelineService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class ComicWorkTimelineServiceImpl extends ServiceImpl<ComicWorkTimelineMapper, ComicWorkTimeline> implements ComicWorkTimelineService {

    @Autowired
    private ComicWorkService comicWorkService;

    /**
     * 将可能是 taskId 的输入解析为实际 work.id：
     * 1) 按 taskId 查 comic_work；2) 再按 work.id 查；3) 都查不到返回原值（兼容老数据）
     */
    private Long resolveWorkId(Long maybeTaskId) {
        if (maybeTaskId == null) return null;
        try {
            ComicWork w = comicWorkService.lambdaQuery()
                    .eq(ComicWork::getTaskId, maybeTaskId)
                    .last("LIMIT 1")
                    .one();
            if (w != null) return w.getId();
        } catch (Exception ignore) { /* ignore */ }
        try {
            ComicWork w = comicWorkService.getById(maybeTaskId);
            if (w != null) return w.getId();
        } catch (Exception ignore) { /* ignore */ }
        return maybeTaskId;
    }

    @Override
    public ComicWorkTimeline addTimeline(Long workId, Long sceneGroupId, Long storyboardId,
                                         String videoUrl, Integer orderIndex, Integer duration) {
        ComicWorkTimeline timeline = new ComicWorkTimeline();
        timeline.setWorkId(workId);
        timeline.setSceneGroupId(sceneGroupId);
        timeline.setStoryboardId(storyboardId);
        timeline.setVideoUrl(videoUrl);
        timeline.setOrderIndex(orderIndex);
        timeline.setDuration(duration);
        this.save(timeline);
        log.info("作品时间线添加成功 workId={}, orderIndex={}", workId, orderIndex);
        return timeline;
    }

    @Override
    public List<ComicWorkTimeline> listByWorkId(Long workId) {
        Long effectiveWorkId = resolveWorkId(workId);
        try {
            return this.list(new LambdaQueryWrapper<ComicWorkTimeline>()
                    .eq(ComicWorkTimeline::getWorkId, effectiveWorkId)
                    .orderByAsc(ComicWorkTimeline::getOrderIndex));
        } catch (Exception e) {
            log.warn("作品时间线查询失败，疑似表未创建，兜底返回空列表 workId={}: {}", effectiveWorkId, e.getMessage());
            return List.of();
        }
    }

    @Override
    @Transactional
    public void reorder(List<ReorderItem> items) {
        for (ReorderItem item : items) {
            ComicWorkTimeline timeline = this.getById(item.id());
            if (timeline != null) {
                timeline.setOrderIndex(item.orderIndex());
                this.updateById(timeline);
            }
        }
        log.info("作品时间线批量重排完成，共{}条", items.size());
    }
}
