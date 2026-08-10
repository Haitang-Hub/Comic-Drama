package com.comicdrama.resource.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.comicdrama.resource.entity.ComicWorkTimeline;

import java.util.List;

public interface ComicWorkTimelineService extends IService<ComicWorkTimeline> {

    ComicWorkTimeline addTimeline(Long workId, Long sceneGroupId, Long storyboardId,
                                  String videoUrl, Integer orderIndex, Integer duration);

    List<ComicWorkTimeline> listByWorkId(Long workId);

    void reorder(List<ReorderItem> items);

    record ReorderItem(Long id, Integer orderIndex) {}
}
