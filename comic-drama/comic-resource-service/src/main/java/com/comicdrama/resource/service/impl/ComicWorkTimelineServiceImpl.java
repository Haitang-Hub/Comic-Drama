package com.comicdrama.resource.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.comicdrama.resource.entity.ComicWorkTimeline;
import com.comicdrama.resource.mapper.ComicWorkTimelineMapper;
import com.comicdrama.resource.service.ComicWorkTimelineService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class ComicWorkTimelineServiceImpl extends ServiceImpl<ComicWorkTimelineMapper, ComicWorkTimeline> implements ComicWorkTimelineService {

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
        return this.list(new LambdaQueryWrapper<ComicWorkTimeline>()
                .eq(ComicWorkTimeline::getWorkId, workId)
                .orderByAsc(ComicWorkTimeline::getOrderIndex));
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
