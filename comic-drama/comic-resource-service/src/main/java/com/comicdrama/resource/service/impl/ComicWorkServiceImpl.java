package com.comicdrama.resource.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.comicdrama.common.dto.PageQuery;
import com.comicdrama.common.exception.BizException;
import com.comicdrama.common.result.PageResult;
import com.comicdrama.common.result.ResultCode;
import com.comicdrama.common.storage.StorageService;
import com.comicdrama.common.util.SecurityUtils;
import com.comicdrama.resource.entity.ComicWork;
import com.comicdrama.resource.mapper.ComicWorkMapper;
import com.comicdrama.resource.service.ComicWorkService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@Service
public class ComicWorkServiceImpl extends ServiceImpl<ComicWorkMapper, ComicWork> implements ComicWorkService {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private StorageService storageService;
    @Autowired
    private ObjectMapper objectMapper;

    @Value("${app.video-merge.zip-url-expire-seconds:604800}")
    private int zipUrlExpireSeconds;

    @Value("${app.video-merge.download-timeout-seconds:300}")
    private int downloadTimeoutSeconds;

    private static String decodeTitle(String title) {
        if (!StringUtils.hasText(title)) return title;
        try {
            String decoded = URLDecoder.decode(title, StandardCharsets.UTF_8.name());
            if (!decoded.equals(title) || title.chars().filter(c -> c == '%').count() > 0) {
                return decoded;
            }
        } catch (Exception e) {
            log.warn("URL解码标题失败: {}", title);
        }
        return title;
    }

    private ComicWork hydrateExtraFields(ComicWork work) {
        if (work == null) return null;
        try {
            Map<String, Object> row = jdbcTemplate.queryForMap(
                    "SELECT zip_url, segment_count, merged_from, aspect_ratio, file_size, " +
                            "share_token, share_expire, publish_time FROM comic_work WHERE id = ?",
                    work.getId());
            if (row.containsKey("zip_url")) work.setZipUrl((String) row.get("zip_url"));
            if (row.containsKey("segment_count") && row.get("segment_count") != null) {
                work.setSegmentCount(((Number) row.get("segment_count")).intValue());
            }
            if (row.containsKey("merged_from")) work.setMergedFrom((String) row.get("merged_from"));
            if (row.containsKey("aspect_ratio")) work.setAspectRatio((String) row.get("aspect_ratio"));
            if (row.containsKey("file_size") && row.get("file_size") != null) {
                work.setFileSize(((Number) row.get("file_size")).longValue());
            }
            if (row.containsKey("share_token")) work.setShareToken((String) row.get("share_token"));
            if (row.containsKey("share_expire") && row.get("share_expire") != null) {
                work.setShareExpire((LocalDateTime) row.get("share_expire"));
            }
            if (row.containsKey("publish_time") && row.get("publish_time") != null) {
                work.setPublishTime((LocalDateTime) row.get("publish_time"));
            }
        } catch (Exception e) {
            log.debug("hydrateExtraFields 失败（新列可能尚未添加）: {}", e.getMessage());
        }
        return work;
    }

    private static ComicWork sanitizeWork(ComicWork work) {
        if (work != null) {
            work.setTitle(decodeTitle(work.getTitle()));
            if (work.getZipUrl() == null && StringUtils.hasText(work.getVideoUrl())
                    && work.getVideoUrl().toLowerCase().endsWith(".zip")) {
                work.setZipUrl(work.getVideoUrl());
            }
            if (work.getSegmentCount() == null) work.setSegmentCount(0);
            if (work.getFileSize() == null) work.setFileSize(0L);
            if (work.getViewCount() == null) work.setViewCount(0);
            if (work.getLikeCount() == null) work.setLikeCount(0);
            if (work.getIsPublic() == null) work.setIsPublic(0);
        }
        return work;
    }

    @Override
    public ComicWork createWork(Long taskId, String title, String coverUrl, String finalVideoUrl, String primaryVideoUrl,
                                String resolution, Integer duration, Long userId) {
        ComicWork work = new ComicWork();
        work.setWorkNo("WK" + IdUtil.getSnowflakeNextIdStr());
        work.setTaskId(taskId);
        Long effectiveUserId = userId != null ? userId : SecurityUtils.getCurrentUserIdOrNull();
        work.setUserId(effectiveUserId != null ? effectiveUserId : 1L);
        work.setTitle(title);
        work.setCoverUrl(coverUrl);
        work.setVideoUrl(StringUtils.hasText(primaryVideoUrl) ? primaryVideoUrl : finalVideoUrl);
        work.setResolution(resolution);
        work.setDuration(duration);
        work.setStatus(1);
        work.setIsPublic(0);
        work.setViewCount(0);
        work.setLikeCount(0);
        this.save(work);
        log.info("作品创建成功 workNo={}, taskId={}, userId={}, title={}",
                work.getWorkNo(), taskId, work.getUserId(), title);
        return work;
    }

    @Override
    public ComicWork getByTaskId(Long taskId) {
        ComicWork work = this.getOne(new LambdaQueryWrapper<ComicWork>()
                .eq(ComicWork::getTaskId, taskId)
                .last("LIMIT 1"));
        return work != null ? sanitizeWork(hydrateExtraFields(work)) : null;
    }

    /**
     * 根据 id 懒创建/获取 ComicWork：
     * 由于列表页 id = task.id，单条查询需要先用 taskId 匹配；
     * 若 comic_work 不存在则从 comic_task 读取信息懒创建一条，保证编辑/分享等写入操作有载体。
     */
    private ComicWork getOrCreateByTaskId(Long id) {
        if (id == null) return null;
        // 1) 先按 taskId 查
        ComicWork work = this.getOne(new LambdaQueryWrapper<ComicWork>()
                .eq(ComicWork::getTaskId, id)
                .last("LIMIT 1"));
        if (work != null) return work;
        // 2) 再按 work.id 查（老数据兼容）
        work = super.getById(id);
        if (work != null) return work;
        // 3) 从 comic_task 读取，懒创建
        try {
            Map<String, Object> row = jdbcTemplate.queryForMap(
                    "SELECT id, task_no, user_id, title, cover_url, final_video_url, " +
                            "resolution, duration, aspect_ratio, create_time " +
                            "FROM comic_task WHERE id = ? LIMIT 1", id);
            if (row != null && !row.isEmpty()) {
                Long taskId = ((Number) row.get("id")).longValue();
                ComicWork nw = new ComicWork();
                nw.setTaskId(taskId);
                Object taskNo = row.get("task_no");
                nw.setWorkNo(taskNo != null ? taskNo.toString() : ("WK" + taskId));
                Object uid = row.get("user_id");
                nw.setUserId(uid != null ? ((Number) uid).longValue() : 1L);
                nw.setTitle(row.get("title") != null ? row.get("title").toString() : null);
                nw.setCoverUrl(row.get("cover_url") != null ? row.get("cover_url").toString() : null);
                Object video = row.get("final_video_url");
                nw.setVideoUrl(video != null ? video.toString() : null);
                nw.setResolution(row.get("resolution") != null ? row.get("resolution").toString() : null);
                Object dur = row.get("duration");
                nw.setDuration(dur != null ? ((Number) dur).intValue() : null);
                Object ar = row.get("aspect_ratio");
                nw.setAspectRatio(ar != null ? ar.toString() : null);
                nw.setStatus(1);
                nw.setIsPublic(0);
                nw.setViewCount(0);
                nw.setLikeCount(0);
                this.save(nw);
                log.info("懒创建作品记录: taskId={}, workId={}, title={}", taskId, nw.getId(), nw.getTitle());
                return nw;
            }
        } catch (Exception e) {
            log.warn("懒创建作品失败: id={}, msg={}", id, e.getMessage());
        }
        return null;
    }

    @Override
    public ComicWork getById(Long id) {
        ComicWork work = getOrCreateByTaskId(id);
        if (work == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND);
        }
        return sanitizeWork(hydrateExtraFields(work));
    }

    @Override
    public PageResult<ComicWork> page(PageQuery query, String keyword, Integer status, Long userId) {
        // 简化：作品列表不再使用 comic_work 表，直接查询 comic_task 中「已完成」(status=2) 的任务
        // 这样无需维护 comic_work 同步，列表只显示已完成任务
        long pageIdx = Math.max(1L, query.getPage());
        long pageSize = Math.max(1L, query.getSize());
        long offset = (pageIdx - 1) * pageSize;

        StringBuilder whereSql = new StringBuilder("WHERE t.status = 2");
        java.util.List<Object> args = new java.util.ArrayList<>();
        if (StringUtils.hasText(keyword)) {
            whereSql.append(" AND (t.task_no LIKE ? OR t.title LIKE ?)");
            args.add("%" + keyword + "%");
            args.add("%" + keyword + "%");
        }
        if (userId != null) {
            whereSql.append(" AND t.user_id = ?");
            args.add(userId);
        }

        // count
        String countSql = "SELECT COUNT(*) FROM comic_task t " + whereSql;
        Long total;
        try {
            total = jdbcTemplate.queryForObject(countSql, Long.class, args.toArray());
        } catch (Exception e) {
            log.warn("作品列表查询任务总数失败: {}", e.getMessage());
            total = 0L;
        }
        if (total == null) total = 0L;

        java.util.List<ComicWork> records = new java.util.ArrayList<>();
        if (total > 0) {
            String listSql = "SELECT t.id, t.task_no, t.user_id, t.title, t.cover_url, t.final_video_url, " +
                    "t.duration, t.resolution, t.aspect_ratio, t.create_time, t.update_time " +
                    "FROM comic_task t " + whereSql +
                    " ORDER BY t.update_time DESC, t.id DESC LIMIT ? OFFSET ?";
            java.util.List<Object> listArgs = new java.util.ArrayList<>(args);
            listArgs.add(pageSize);
            listArgs.add(offset);
            try {
                records = jdbcTemplate.query(listSql, (rs, rowNum) -> {
                    ComicWork w = new ComicWork();
                    // 用 task.id 作为 work.id，保持前端 API（分享/编辑）按 id 调用的兼容性
                    w.setId(rs.getLong("id"));
                    String taskNo = rs.getString("task_no");
                    w.setWorkNo(taskNo != null ? taskNo : ("WK" + rs.getLong("id")));
                    w.setTaskId(rs.getLong("id"));
                    w.setUserId(rs.getObject("user_id") != null ? rs.getLong("user_id") : null);
                    w.setTitle(decodeTitle(rs.getString("title")));
                    w.setCoverUrl(rs.getString("cover_url"));
                    w.setVideoUrl(rs.getString("final_video_url"));
                    w.setDuration(rs.getObject("duration") != null ? rs.getInt("duration") : null);
                    w.setResolution(rs.getString("resolution"));
                    w.setAspectRatio(rs.getString("aspect_ratio"));
                    w.setStatus(1);
                    w.setIsPublic(0);
                    w.setViewCount(0);
                    w.setLikeCount(0);
                    w.setSegmentCount(0);
                    w.setFileSize(0L);
                    w.setCreateTime(rs.getTimestamp("create_time") != null
                            ? rs.getTimestamp("create_time").toLocalDateTime() : null);
                    w.setUpdateTime(rs.getTimestamp("update_time") != null
                            ? rs.getTimestamp("update_time").toLocalDateTime() : null);
                    return w;
                }, listArgs.toArray());
            } catch (Exception e) {
                log.warn("作品列表查询任务分页失败: {}", e.getMessage());
                records = new java.util.ArrayList<>();
            }
        }

        records = records.stream()
                .map(ComicWorkServiceImpl::sanitizeWork)
                .collect(Collectors.toList());
        return new PageResult<>(records, total, pageIdx, pageSize);
    }

    @Override
    public String generateShareToken(Long id, int expireHours) {
        ComicWork work = getOrCreateByTaskId(id);
        if (work == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND);
        }
        String token = UUID.randomUUID().toString().replace("-", "") + IdUtil.getSnowflakeNextIdStr();
        LocalDateTime expire = LocalDateTime.now().plusHours(expireHours);
        try {
            jdbcTemplate.update(
                    "UPDATE comic_work SET share_token = ?, share_expire = ? WHERE id = ?",
                    token, expire, work.getId());
        } catch (Exception e) {
            log.warn("写入分享令牌失败（share_token 列可能尚未添加）: {}", e.getMessage());
            throw new BizException("数据库尚未添加 share_token 列，请先执行 SQL 迁移脚本");
        }
        work.setShareToken(token);
        work.setShareExpire(expire);
        return token;
    }

    @Override
    public ComicWork getByShareToken(String token) {
        if (!StringUtils.hasText(token)) return null;
        try {
            List<ComicWork> works = jdbcTemplate.query(
                    "SELECT id, work_no, task_id, user_id, title, cover_url, video_url, " +
                            "duration, resolution, status, is_public, view_count, like_count " +
                            "FROM comic_work WHERE share_token = ? AND share_expire > NOW() LIMIT 1",
                    (rs, rowNum) -> {
                        ComicWork w = new ComicWork();
                        w.setId(rs.getLong("id"));
                        w.setWorkNo(rs.getString("work_no"));
                        w.setTaskId(rs.getLong("task_id"));
                        w.setUserId(rs.getLong("user_id"));
                        w.setTitle(rs.getString("title"));
                        w.setCoverUrl(rs.getString("cover_url"));
                        w.setVideoUrl(rs.getString("video_url"));
                        w.setDuration(rs.getInt("duration"));
                        w.setResolution(rs.getString("resolution"));
                        w.setStatus(rs.getInt("status"));
                        w.setIsPublic(rs.getInt("is_public"));
                        w.setViewCount(rs.getInt("view_count"));
                        w.setLikeCount(rs.getInt("like_count"));
                        return w;
                    }, token);
            if (!works.isEmpty()) {
                ComicWork work = works.get(0);
                jdbcTemplate.update("UPDATE comic_work SET view_count = view_count + 1 WHERE id = ?", work.getId());
                work.setViewCount((work.getViewCount() == null ? 0 : work.getViewCount()) + 1);
                return sanitizeWork(hydrateExtraFields(work));
            }
        } catch (Exception e) {
            log.warn("按分享令牌查询失败（share_token 列可能尚未添加）: {}", e.getMessage());
        }
        return null;
    }

    @Override
    public void incrementViewCount(Long id) {
        try {
            ComicWork work = getOrCreateByTaskId(id);
            if (work == null || work.getId() == null) return;
            jdbcTemplate.update("UPDATE comic_work SET view_count = COALESCE(view_count, 0) + 1 WHERE id = ?", work.getId());
        } catch (Exception e) {
            log.warn("浏览量更新失败: {}", e.getMessage());
        }
    }

    // =====================================================================
    // 懒打包：按用户点击下载时按需打包ZIP，省CPU/存储，重生成视频时不白打包
    // =====================================================================

    @Override
    public String buildAndGetZipDownloadUrl(Long workOrTaskId) {
        // 1) 解析 taskId + workId（id 可能是 comic_task.id 或 comic_work.id）
        long[] resolved = resolveTaskAndWorkId(workOrTaskId);
        long taskId = resolved[0];
        long workId = resolved[1];
        log.info("[downloadZip] 开始按需打包ZIP workOrTaskId={} -> taskId={}, workId={}", workOrTaskId, taskId, workId);

        // 2) 快速路径：若 zip_object_key 存在且存储有该 object -> 直接重新签名，跳过打包
        String cachedObjectKey = queryZipObjectKey(taskId, workId);
        if (StringUtils.hasText(cachedObjectKey)) {
            try {
                if (storageService.exists(cachedObjectKey)) {
                    String signed = storageService.signUrl(cachedObjectKey, zipUrlExpireSeconds);
                    log.info("[downloadZip] 命中缓存，直接重签名 taskId={}, objectKey={}", taskId, cachedObjectKey);
                    return signed;
                } else {
                    log.warn("[downloadZip] zip_object_key={} 在存储中不存在，视为缓存失效，重新打包, taskId={}", cachedObjectKey, taskId);
                }
            } catch (Exception e) {
                log.warn("[downloadZip] 缓存检查/签名失败，重新打包 taskId={}: {}", taskId, e.getMessage());
            }
        }

        // 3) 获取（或构建）manifest JSON，解析视频清单（filename, originalUrl）
        String manifestJson = getOrBuildManifestJson(taskId);
        Map<String, Object> manifest;
        try {
            manifest = objectMapper.readValue(manifestJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new BizException("解析 manifest.json 失败：" + e.getMessage());
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> videos = (List<Map<String, Object>>) manifest.get("videos");
        if (videos == null || videos.isEmpty()) {
            throw new BizException("没有可打包的场景视频，manifest 为空");
        }

        // 4) 准备工作目录
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("comic-zip-" + taskId + "-");
            log.info("[downloadZip] 临时目录={}, 视频段数={}, taskId={}", tempDir, videos.size(), taskId);

            // 5) 下载所有场景视频 -> 按序编号文件名
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
            int total = videos.size();
            List<VideoZipItem> items = new ArrayList<>(total);
            for (int i = 0; i < total; i++) {
                Map<String, Object> v = videos.get(i);
                String filename = (String) v.get("filename");
                String originalUrl = (String) v.get("originalUrl");
                if (!StringUtils.hasText(filename) || !StringUtils.hasText(originalUrl)) {
                    throw new BizException("manifest 中第 " + (i + 1) + " 段视频缺少 filename/originalUrl");
                }
                Path target = tempDir.resolve(filename);
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(originalUrl))
                        .timeout(Duration.ofSeconds(downloadTimeoutSeconds))
                        .GET().build();
                HttpResponse<InputStream> resp = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
                if (resp.statusCode() != 200) {
                    throw new BizException("下载场景视频失败 HTTP " + resp.statusCode()
                            + ", filename=" + filename + ", url=" + originalUrl);
                }
                try (InputStream is = resp.body()) {
                    Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
                }
                items.add(new VideoZipItem(filename, target));
                log.debug("[downloadZip] 下载 {}/{}: {}", i + 1, total, filename);
            }

            // 6) 写 manifest.json
            Path manifestPath = tempDir.resolve("manifest.json");
            Files.writeString(manifestPath, manifestJson, StandardCharsets.UTF_8);

            // 7) 打包 ZIP
            String taskNo = manifest.get("taskNo") != null ? manifest.get("taskNo").toString() : String.valueOf(taskId);
            String zipFileName = "comic-" + taskNo + ".zip";
            Path zipPath = tempDir.resolve(zipFileName);
            long zipSize = buildZipFile(items, manifestPath, zipPath);
            log.info("[downloadZip] ZIP打包完成 size={}B, filename={}, taskId={}", zipSize, zipFileName, taskId);

            // 8) 上传存储
            String zipObjectKey = "task/" + taskId + "/final/" + zipFileName;
            try (InputStream zipIs = Files.newInputStream(zipPath)) {
                storageService.upload(zipIs, zipObjectKey, zipSize, "application/zip");
            }
            log.info("[downloadZip] ZIP上传成功 objectKey={}, taskId={}", zipObjectKey, taskId);

            // 9) 回填 DB（comic_task.final_video_url + zip_object_key，comic_work.zip_url + file_size + zip_object_key）
            String signedUrl = storageService.signUrl(zipObjectKey, zipUrlExpireSeconds);
            fillBackZipCache(taskId, workId, signedUrl, zipObjectKey, zipSize);

            log.info("[downloadZip] 按需打包完成 taskId={}, signedUrl.len={}", taskId, signedUrl.length());
            return signedUrl;

        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("[downloadZip] 按需打包失败 taskId={}", taskId, e);
            throw new BizException("打包成片ZIP失败：" + e.getMessage());
        } finally {
            cleanupTempDirQuietly(tempDir);
        }
    }

    /**
     * 解析 workOrTaskId，返回 [taskId, workId]。
     * 逻辑与 getOrCreateByTaskId 对齐：先用 taskId 匹配 comic_work.task_id，再用 work.id，实在没有按 task.id 查 comic_task。
     */
    private long[] resolveTaskAndWorkId(Long workOrTaskId) {
        if (workOrTaskId == null) throw new BizException("ID不能为空");
        // 1) taskId 匹配
        ComicWork byTask = this.getOne(new LambdaQueryWrapper<ComicWork>()
                .eq(ComicWork::getTaskId, workOrTaskId).last("LIMIT 1"));
        if (byTask != null && byTask.getId() != null) {
            return new long[]{ byTask.getTaskId() != null ? byTask.getTaskId() : workOrTaskId, byTask.getId() };
        }
        // 2) work.id 匹配
        ComicWork byWork = super.getById(workOrTaskId);
        if (byWork != null && byWork.getId() != null) {
            long tid = byWork.getTaskId() != null ? byWork.getTaskId() : workOrTaskId;
            return new long[]{ tid, byWork.getId() };
        }
        // 3) 当作 taskId 查 comic_task
        try {
            Map<String, Object> row = jdbcTemplate.queryForMap(
                    "SELECT id FROM comic_task WHERE id = ? LIMIT 1", workOrTaskId);
            if (row != null && row.get("id") != null) {
                long taskId = ((Number) row.get("id")).longValue();
                // 顺便懒创建 ComicWork
                ComicWork nw = getOrCreateByTaskId(taskId);
                long wid = nw != null && nw.getId() != null ? nw.getId() : taskId;
                return new long[]{ taskId, wid };
            }
        } catch (Exception ignored) {}
        throw new BizException("未找到任务或作品，ID=" + workOrTaskId);
    }

    /** 查询缓存的 zip_object_key（优先查 comic_task，再查 comic_work；列不存在返回 null）。 */
    private String queryZipObjectKey(long taskId, long workId) {
        try {
            Map<String, Object> row = jdbcTemplate.queryForMap(
                    "SELECT zip_object_key FROM comic_task WHERE id = ? LIMIT 1", taskId);
            Object v = row.get("zip_object_key");
            if (v instanceof String s && StringUtils.hasText(s)) return s;
        } catch (Exception e) {
            log.debug("[downloadZip] comic_task.zip_object_key 查询失败（列可能未添加）：{}", e.getMessage());
        }
        try {
            Map<String, Object> row = jdbcTemplate.queryForMap(
                    "SELECT zip_object_key FROM comic_work WHERE id = ? LIMIT 1", workId);
            Object v = row.get("zip_object_key");
            if (v instanceof String s && StringUtils.hasText(s)) return s;
        } catch (Exception e) {
            log.debug("[downloadZip] comic_work.zip_object_key 查询失败（列可能未添加）：{}", e.getMessage());
        }
        return null;
    }

    /** 获取或构建 manifest（直接复用 comic_task.final_work_manifest，否则从 scene_video 构建）。 */
    private String getOrBuildManifestJson(Long taskId) {
        try {
            Map<String, Object> row = jdbcTemplate.queryForMap(
                    "SELECT final_work_manifest FROM comic_task WHERE id = ? LIMIT 1", taskId);
            Object v = row.get("final_work_manifest");
            if (v instanceof String s && StringUtils.hasText(s)) return s;
        } catch (Exception e) {
            log.debug("[downloadZip] 读 comic_task.final_work_manifest 失败: {}", e.getMessage());
        }
        // fallback：从 scene_video 动态构建（参考 TaskServiceImpl.getOrBuildFinalWorkManifest）
        log.info("[downloadZip] final_work_manifest 为空，从 scene_video 动态构建 taskId={}", taskId);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, scene_group_id, storyboard_ids, video_url, base_frame_url, thumbnail_url, duration, resolution " +
                        "FROM scene_video WHERE task_id = ? " +
                        "ORDER BY scene_group_id ASC, " +
                        "  CAST(SUBSTRING_INDEX(IFNULL(CONCAT(storyboard_ids, ','), '999999999,'), ',', 1) AS UNSIGNED) ASC, id ASC",
                taskId);
        int totalDuration = 0;
        List<Map<String, Object>> entries = new ArrayList<>();
        int seq = 0;
        for (Map<String, Object> r : rows) {
            seq++;
            Object durationObj = r.get("duration");
            Integer duration = durationObj instanceof Number ? ((Number) durationObj).intValue() : 0;
            totalDuration += duration;
            Long sceneGroupId = r.get("scene_group_id") != null ? ((Number) r.get("scene_group_id")).longValue() : null;
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("orderIndex", seq);
            entry.put("filename", String.format("%03d_scene%s.mp4",
                    seq, sceneGroupId != null ? sceneGroupId.toString() : String.valueOf(seq)));
            entry.put("sceneGroupId", sceneGroupId);
            entry.put("storyboardSeqRange", (sceneGroupId != null) ? ("scene_" + sceneGroupId) : ("片段_" + seq));
            entry.put("duration", duration);
            entry.put("originalUrl", r.get("video_url"));
            entry.put("coverUrl", r.get("base_frame_url") != null ? r.get("base_frame_url") : r.get("thumbnail_url"));
            entries.add(entry);
        }
        try {
            Map<String, Object> taskRow = jdbcTemplate.queryForMap(
                    "SELECT task_no, title, resolution, aspect_ratio FROM comic_task WHERE id = ? LIMIT 1", taskId);
            Map<String, Object> manifest = new LinkedHashMap<>();
            manifest.put("taskId", taskId);
            manifest.put("taskNo", taskRow.get("task_no"));
            manifest.put("title", taskRow.get("title"));
            manifest.put("totalDuration", totalDuration);
            manifest.put("segmentCount", entries.size());
            manifest.put("resolution", taskRow.get("resolution"));
            manifest.put("aspectRatio", taskRow.get("aspect_ratio"));
            manifest.put("createdAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            manifest.put("videos", entries);
            String json = objectMapper.writeValueAsString(manifest);
            try {
                jdbcTemplate.update("UPDATE comic_task SET final_work_manifest = ? WHERE id = ?", json, taskId);
            } catch (Exception e) {
                log.warn("[downloadZip] 回写 final_work_manifest 失败 taskId={}: {}", taskId, e.getMessage());
            }
            return json;
        } catch (Exception e) {
            throw new BizException("构建播放清单失败：" + e.getMessage());
        }
    }

    private record VideoZipItem(String filename, Path path) {}

    private long buildZipFile(List<VideoZipItem> items, Path manifestPath, Path zipPath) throws Exception {
        try (ZipOutputStream zos = new ZipOutputStream(
                new BufferedOutputStream(Files.newOutputStream(zipPath)))) {
            addZipEntry(zos, "manifest.json", manifestPath);
            for (VideoZipItem it : items) {
                addZipEntry(zos, it.filename(), it.path());
            }
        }
        return Files.size(zipPath);
    }

    private void addZipEntry(ZipOutputStream zos, String entryName, Path file) throws Exception {
        zos.putNextEntry(new ZipEntry(entryName));
        try (InputStream is = Files.newInputStream(file)) {
            is.transferTo(zos);
        }
        zos.closeEntry();
    }

    /** 回填 zip_url/zip_object_key/file_size 到 comic_task 和 comic_work，列不存在就忽略。 */
    private void fillBackZipCache(long taskId, long workId, String signedUrl, String objectKey, long fileSize) {
        try {
            jdbcTemplate.update(
                    "UPDATE comic_task SET final_video_url = ? WHERE id = ?", signedUrl, taskId);
        } catch (Exception e) {
            log.warn("[downloadZip] 回填 comic_task.final_video_url 失败: {}", e.getMessage());
        }
        try {
            jdbcTemplate.update(
                    "UPDATE comic_task SET zip_object_key = ? WHERE id = ?", objectKey, taskId);
        } catch (Exception e) {
            log.debug("[downloadZip] 回填 comic_task.zip_object_key 失败（列可能未添加）: {}", e.getMessage());
        }
        try {
            jdbcTemplate.update(
                    "UPDATE comic_work SET zip_url = ?, file_size = ? WHERE id = ? OR task_id = ?",
                    signedUrl, fileSize, workId, taskId);
        } catch (Exception e) {
            log.warn("[downloadZip] 回填 comic_work.zip_url/file_size 失败: {}", e.getMessage());
        }
        try {
            jdbcTemplate.update(
                    "UPDATE comic_work SET zip_object_key = ? WHERE id = ? OR task_id = ?",
                    objectKey, workId, taskId);
        } catch (Exception e) {
            log.debug("[downloadZip] 回填 comic_work.zip_object_key 失败（列可能未添加）: {}", e.getMessage());
        }
    }

    private void cleanupTempDirQuietly(Path tempDir) {
        if (tempDir == null || !Files.exists(tempDir)) return;
        try (Stream<Path> walk = Files.walk(tempDir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (Exception ignored) {}
            });
        } catch (Exception e) {
            log.debug("[downloadZip] 清理临时目录失败 {}: {}", tempDir, e.getMessage());
        }
    }
}
