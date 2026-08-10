package com.comicdrama.resource.controller;

import com.comicdrama.common.result.Result;
import com.comicdrama.resource.entity.ComicWorkTimeline;
import com.comicdrama.resource.service.ComicWorkTimelineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/work/timeline")
@RequiredArgsConstructor
public class ComicWorkTimelineController {

    private final ComicWorkTimelineService comicWorkTimelineService;

    @GetMapping("/{workId}")
    public Result<List<ComicWorkTimeline>> listByWorkId(@PathVariable Long workId) {
        return Result.ok(comicWorkTimelineService.listByWorkId(workId));
    }

    @PostMapping
    public Result<ComicWorkTimeline> add(@RequestBody ComicWorkTimeline timeline) {
        comicWorkTimelineService.save(timeline);
        return Result.ok(timeline);
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody ComicWorkTimeline timeline) {
        timeline.setId(id);
        comicWorkTimelineService.updateById(timeline);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        comicWorkTimelineService.removeById(id);
        return Result.ok();
    }

    @PostMapping("/reorder")
    public Result<Void> reorder(@RequestBody List<ComicWorkTimelineService.ReorderItem> items) {
        comicWorkTimelineService.reorder(items);
        return Result.ok();
    }
}
