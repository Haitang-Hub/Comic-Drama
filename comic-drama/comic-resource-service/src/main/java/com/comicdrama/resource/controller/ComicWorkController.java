package com.comicdrama.resource.controller;

import com.comicdrama.common.dto.PageQuery;
import com.comicdrama.common.result.PageResult;
import com.comicdrama.common.result.Result;
import com.comicdrama.common.util.SecurityUtils;
import com.comicdrama.resource.dto.WorkCreateDTO;
import com.comicdrama.resource.entity.ComicWork;
import com.comicdrama.resource.service.ComicWorkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

@Slf4j
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
        // 前端传入的 id 可能是 task.id（列表来自 comic_task），通过 getById 懒创建 work 记录
        Long refId = work.getId();
        ComicWork existing = comicWorkService.getById(refId); // 内部已做 taskId 优先 + 懒创建
        work.setId(existing.getId());
        work.setTaskId(existing.getTaskId() != null ? existing.getTaskId() : refId);
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

    /**
     * 按需（懒）打包成片 ZIP 并返回下载地址。
     * <ul>
     *   <li>缓存未命中：实时下载场景视频 → 生成manifest → 打包ZIP → 上传存储 → 返回签名URL（首次点击耗时较长）</li>
     *   <li>缓存命中且存储存在：直接基于 zip_object_key 重新生成签名 URL 返回（秒级）</li>
     * </ul>
     *
     * @param id    作品ID 或 任务ID（懒创建作品场景）
     * @param redirect 是否直接 302 跳转到下载地址（浏览器直接下载）。默认 true；传 false 则返回 JSON 包 URL 字符串，前端自行 window.location。
     */
    @GetMapping("/{id}/download-zip")
    public ResponseEntity<?> downloadZip(@PathVariable Long id,
                                          @RequestParam(defaultValue = "true") boolean redirect) {
        long start = System.currentTimeMillis();
        try {
            String signedUrl = comicWorkService.buildAndGetZipDownloadUrl(id);
            long cost = System.currentTimeMillis() - start;
            log.info("[downloadZip] 接口完成 id={}, redirect={}, cost={}ms", id, redirect, cost);

            if (!redirect) {
                return ResponseEntity.ok(com.comicdrama.common.result.Result.ok(signedUrl));
            }
            // 直接 302 重定向到签名下载地址，浏览器会自动下载
            HttpHeaders headers = new HttpHeaders();
            headers.setLocation(java.net.URI.create(signedUrl));
            return ResponseEntity.status(HttpStatus.FOUND).headers(headers).build();
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - start;
            log.warn("[downloadZip] 失败 id={}, cost={}ms: {}", id, cost, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(com.comicdrama.common.result.Result.fail("打包失败：" + e.getMessage()));
        }
    }
}
