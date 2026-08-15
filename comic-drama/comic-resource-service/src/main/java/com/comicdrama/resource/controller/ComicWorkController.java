package com.comicdrama.resource.controller;

import com.comicdrama.common.dto.PageQuery;
import com.comicdrama.common.result.PageResult;
import com.comicdrama.common.result.Result;
import com.comicdrama.common.util.SecurityUtils;
import com.comicdrama.resource.dto.WorkCreateDTO;
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
    public Result<ComicWork> create(@RequestBody WorkCreateDTO dto) {
        return Result.ok(comicWorkService.createWork(
                dto.getTaskId(), dto.getTitle(), dto.getCoverUrl(),
                dto.getFinalVideoUrl(), dto.getPrimaryVideoUrl(),
                dto.getResolution(), dto.getDuration(), dto.getUserId()));
    }

    @GetMapping("/task/{taskId}")
    public Result<ComicWork> getByTaskId(@PathVariable Long taskId) {
        return Result.ok(comicWorkService.getByTaskId(taskId));
    }

    @GetMapping("/page")
    public Result<PageResult<ComicWork>> page(PageQuery query,
                                                @RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) Integer status) {
        Long userId = SecurityUtils.getCurrentUserIdOrNull();
        return Result.ok(comicWorkService.page(query, keyword, status, userId));
    }

    @GetMapping("/{id}")
    public Result<ComicWork> get(@PathVariable Long id) {
        comicWorkService.incrementViewCount(id);
        return Result.ok(comicWorkService.getById(id));
    }

    @PutMapping
    public Result<Void> update(@RequestBody ComicWork work) {
        comicWorkService.updateById(work);
        return Result.ok();
    }

    @PostMapping("/{id}/share")
    public Result<String> generateShareToken(@PathVariable Long id,
                                              @RequestParam(defaultValue = "72") int expireHours) {
        return Result.ok(comicWorkService.generateShareToken(id, expireHours));
    }

    @GetMapping("/share/{token}")
    public Result<ComicWork> getByShareToken(@PathVariable String token) {
        ComicWork work = comicWorkService.getByShareToken(token);
        if (work == null) {
            return Result.fail("分享链接无效或已过期");
        }
        return Result.ok(work);
    }
}
