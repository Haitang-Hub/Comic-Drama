package com.comicdrama.resource.controller;

import com.comicdrama.common.dto.PageQuery;
import com.comicdrama.common.result.PageResult;
import com.comicdrama.common.result.Result;
import com.comicdrama.resource.entity.ComicWork;
import com.comicdrama.resource.service.ComicWorkService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/work")
@RequiredArgsConstructor
public class ComicWorkController {

    private final ComicWorkService comicWorkService;

    @PostMapping("/create")
    public Result<ComicWork> create(@RequestParam Long taskId,
                                     @RequestParam String title,
                                     @RequestParam(required = false) String coverUrl,
                                     @RequestParam(required = false) String finalVideoUrl,
                                     @RequestParam(required = false) String resolution,
                                     @RequestParam(required = false) Integer duration,
                                     @RequestParam(required = false) Long userId) {
        return Result.ok(comicWorkService.createWork(taskId, title, coverUrl, finalVideoUrl, resolution, duration, userId));
    }

    @GetMapping("/task/{taskId}")
    public Result<ComicWork> getByTaskId(@PathVariable Long taskId) {
        return Result.ok(comicWorkService.getByTaskId(taskId));
    }

    @GetMapping("/page")
    public Result<PageResult<ComicWork>> page(PageQuery query,
                                                @RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) Integer status,
                                                @RequestParam(required = false) Long userId) {
        return Result.ok(comicWorkService.page(query, keyword, status, userId));
    }

    @GetMapping("/{id}")
    public Result<ComicWork> get(@PathVariable Long id) {
        return Result.ok(comicWorkService.getById(id));
    }

    @PutMapping
    public Result<Void> update(@RequestBody ComicWork work) {
        comicWorkService.updateById(work);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        comicWorkService.delete(id);
        return Result.ok();
    }
}
