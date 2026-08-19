package com.comicdrama.resource.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.comicdrama.common.dto.PageQuery;
import com.comicdrama.common.exception.BizException;
import com.comicdrama.common.result.PageResult;
import com.comicdrama.common.result.ResultCode;
import com.comicdrama.common.storage.StorageProperties;
import com.comicdrama.common.storage.StorageService;
import com.comicdrama.common.util.SecurityUtils;
import com.comicdrama.resource.entity.ResourceFile;
import com.comicdrama.resource.mapper.ResourceFileMapper;
import com.comicdrama.resource.service.ResourceFileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResourceFileServiceImpl extends ServiceImpl<ResourceFileMapper, ResourceFile> implements ResourceFileService {

    private final StorageService storageService;
    private final StorageProperties storageProperties;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public ResourceFile upload(MultipartFile file, Long taskId, String sourceType) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new BizException("上传文件不能为空");
        }
        String originalName = file.getOriginalFilename();
        String ext = StringUtils.hasText(originalName) && originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf('.'))
                : "";
        // objectKey：resource/{uuid}{ext}，LocalFileStorageService 会自动追加 yyyy/MM/dd 日期前缀
        String objectKey = "resource/" + IdUtil.fastSimpleUUID() + ext;
        String contentType = file.getContentType();

        String fullKey = storageService.upload(file.getInputStream(), objectKey, file.getSize(), contentType);
        log.info("文件上传成功 objectKey={}, size={}", fullKey, file.getSize());

        ResourceFile record = new ResourceFile();
        record.setTaskId(taskId);
        record.setUserId(SecurityUtils.getCurrentUserIdOrNull());
        record.setFileName(FileUtil.getName(fullKey));
        record.setOriginalName(originalName);
        record.setFileType(detectFileType(contentType, fullKey));
        record.setMimeType(contentType);
        record.setFileSize(file.getSize());
        record.setBucketName(resolveBucket());
        record.setObjectKey(fullKey);
        record.setSourceType(StringUtils.hasText(sourceType) ? sourceType : "user_upload");
        record.setIsPublic(0);
        this.save(record);
        return record;
    }

    @Override
    public PageResult<ResourceFile> page(PageQuery query, Long taskId, Integer fileType) {
        LambdaQueryWrapper<ResourceFile> wrapper = new LambdaQueryWrapper<>();
        if (taskId != null) {
            wrapper.eq(ResourceFile::getTaskId, taskId);
        }
        if (fileType != null) {
            wrapper.eq(ResourceFile::getFileType, fileType);
        }
        wrapper.orderByDesc(ResourceFile::getCreateTime);
        Page<ResourceFile> page = new Page<>(query.getPage(), query.getSize());
        Page<ResourceFile> result = this.page(page, wrapper);
        return new PageResult<>(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public ResourceFile getById(Long id) {
        ResourceFile record = super.getById(id);
        if (record == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND);
        }
        return record;
    }

    @Override
    public void delete(Long id) throws Exception {
        ResourceFile record = super.getById(id);
        if (record == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND);
        }
        try {
            storageService.delete(record.getObjectKey());
        } catch (Exception e) {
            log.warn("物理删除文件失败 objectKey={}, 继续删除数据库记录: {}", record.getObjectKey(), e.getMessage());
        }
        this.removeById(id);
    }

    @Override
    public String signUrl(Long id, int expireSeconds) throws Exception {
        ResourceFile record = super.getById(id);
        if (record == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND);
        }
        String url = storageService.signUrl(record.getObjectKey(), expireSeconds);
        ResourceFile update = new ResourceFile();
        update.setId(id);
        update.setTempUrl(url);
        update.setTempUrlExpire(LocalDateTime.now().plusSeconds(expireSeconds));
        update.setFileUrl(url);
        this.updateById(update);
        return url;
    }

    // ============================================================
    //  Artifact -> resource_file 回填同步
    // ============================================================
    @Override
    public Map<String, Integer> syncFromArtifactTables() {
        int scanned = 0, inserted = 0, skipped = 0;
        try {
            // 1) 收集现存 objectKey 做去重（资源文件表的唯一业务键）
            Set<String> existingKeys = new HashSet<>();
            try {
                this.list().forEach(r -> {
                    if (r.getObjectKey() != null) existingKeys.add(r.getObjectKey());
                });
            } catch (Exception ignored) {
                // 表尚未创建或其他问题，跳过缓存
            }

            String baseUrl = storageProperties.getLocalBaseUrl();
            // 规范化 baseUrl: 去掉末尾 /
            if (baseUrl != null && baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);

            // 2) UNION ALL 各中间产物表中的文件 URL
            //    每一行返回 (url, task_id, user_id, source_node, create_time, file_type_hint(ext))
            String sql = ""
                    + " SELECT image_url AS url, task_id, NULL AS user_id, 'asset_image' AS node, create_time, 'png' AS ext_hint FROM asset_image WHERE image_url IS NOT NULL "
                    + " UNION ALL SELECT image_url AS url, task_id, NULL AS user_id, 'storyboard_image' AS node, create_time, 'png' AS ext_hint FROM storyboard_image WHERE image_url IS NOT NULL "
                    + " UNION ALL SELECT audio_url AS url, task_id, NULL AS user_id, 'storyboard_audio' AS node, create_time, 'mp3' AS ext_hint FROM storyboard_audio WHERE audio_url IS NOT NULL "
                    + " UNION ALL SELECT video_url AS url, task_id, NULL AS user_id, 'scene_video' AS node, create_time, 'mp4' AS ext_hint FROM scene_video WHERE video_url IS NOT NULL "
                    + " UNION ALL SELECT video_url AS url, NULL AS task_id, NULL AS user_id, 'work_timeline' AS node, create_time, 'mp4' AS ext_hint FROM comic_work_timeline WHERE video_url IS NOT NULL "
                    + " UNION ALL SELECT cover_url AS url, task_id, user_id, 'comic_work_cover' AS node, create_time, 'jpg' AS ext_hint FROM comic_work WHERE cover_url IS NOT NULL "
                    + " UNION ALL SELECT video_url AS url, task_id, user_id, 'comic_work_video' AS node, create_time, 'mp4' AS ext_hint FROM comic_work WHERE video_url IS NOT NULL "
                    + " UNION ALL SELECT zip_url   AS url, task_id, user_id, 'comic_work_zip'   AS node, create_time, 'zip' AS ext_hint FROM comic_work WHERE zip_url   IS NOT NULL";

            List<Map<String, Object>> rows;
            try {
                rows = jdbcTemplate.queryForList(sql);
            } catch (Exception e) {
                // 可能某些表不存在（老库无 comic_work_timeline 等场景）：降级为按表逐条探测
                log.warn("[ResourceSync] UNION ALL 查询失败，降级按表逐条探测: {}", e.getMessage());
                rows = probePerTable(baseUrl);
            }

            for (Map<String, Object> row : rows) {
                scanned++;
                String url = asStr(row.get("url"));
                if (!StringUtils.hasText(url)) { skipped++; continue; }

                String objectKey = extractObjectKey(url, baseUrl);
                if (!StringUtils.hasText(objectKey)) { skipped++; continue; }
                if (existingKeys.contains(objectKey)) { skipped++; continue; }

                Long taskId = asLong(row.get("task_id"));
                Long userId = asLong(row.get("user_id"));
                String sourceNode = asStr(row.get("node"));
                LocalDateTime createTime = row.get("create_time") instanceof java.sql.Timestamp ts
                        ? ts.toLocalDateTime()
                        : (row.get("create_time") instanceof LocalDateTime dt ? dt : LocalDateTime.now());
                String extHint = asStr(row.get("ext_hint"));

                // file_type & mimeType
                int fileType = detectFileType(null, objectKey);
                String mimeType = resolveMimeType(extHint, objectKey);

                ResourceFile rec = new ResourceFile();
                rec.setTaskId(taskId);
                rec.setUserId(userId);
                String fileName = FileUtil.getName(objectKey);
                rec.setFileName(StringUtils.hasText(fileName) ? fileName : ("artifact_" + Math.abs(objectKey.hashCode())));
                rec.setOriginalName(rec.getFileName());
                rec.setFileType(fileType);
                rec.setMimeType(mimeType);
                rec.setBucketName(resolveBucket());
                rec.setObjectKey(objectKey);
                rec.setSourceType("pipeline_artifact");
                rec.setSourceNode(StringUtils.hasText(sourceNode) ? sourceNode : "artifact_table");
                rec.setIsPublic(1);
                rec.setFileUrl(url);
                // 尝试获取文件大小
                rec.setFileSize(guessFileSize(objectKey));
                if (createTime != null) rec.setCreateTime(createTime);

                try {
                    this.save(rec);
                    existingKeys.add(objectKey);
                    inserted++;
                } catch (Exception e) {
                    skipped++;
                    log.warn("[ResourceSync] 回填失败 url={} err={}", url, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("[ResourceSync] 同步异常: {}", e.getMessage(), e);
        }
        Map<String, Integer> summary = new LinkedHashMap<>();
        summary.put("scanned", scanned);
        summary.put("inserted", inserted);
        summary.put("skipped", skipped);
        log.info("[ResourceSync] 同步完成 scanned={} inserted={} skipped={}", scanned, inserted, skipped);
        return summary;
    }

    private List<Map<String, Object>> probePerTable(String baseUrl) {
        List<Map<String, Object>> out = new ArrayList<>();
        String[][] probes = new String[][] {
                {"asset_image",         "image_url",          "asset_image",       "png"},
                {"storyboard_image",    "image_url",          "storyboard_image",  "png"},
                {"storyboard_audio",    "audio_url",          "storyboard_audio",  "mp3"},
                {"scene_video",         "video_url",          "scene_video",       "mp4"},
                {"comic_work_timeline", "video_url",          "work_timeline",     "mp4"},
        };
        for (String[] p : probes) {
            try {
                String table = p[0], col = p[1], node = p[2], ext = p[3];
                String q = String.format(
                        "SELECT %s AS url, task_id, NULL AS user_id, '%s' AS node, create_time, '%s' AS ext_hint FROM %s WHERE %s IS NOT NULL",
                        col, node, ext, table, col);
                out.addAll(jdbcTemplate.queryForList(q));
            } catch (Exception ignored) { /* 表不存在 */ }
        }
        // comic_work 多列
        String[] cols = {"cover_url", "video_url", "zip_url"};
        String[] nodes = {"comic_work_cover", "comic_work_video", "comic_work_zip"};
        String[] exts  = {"jpg", "mp4", "zip"};
        for (int i = 0; i < cols.length; i++) {
            try {
                String q = String.format(
                        "SELECT %s AS url, task_id, user_id, '%s' AS node, create_time, '%s' AS ext_hint FROM comic_work WHERE %s IS NOT NULL",
                        cols[i], nodes[i], exts[i], cols[i]);
                out.addAll(jdbcTemplate.queryForList(q));
            } catch (Exception ignored) { /* 表不存在 */ }
        }
        return out;
    }

    /**
     * 从 URL 中提取 objectKey。约定：
     *  - URL 以 localBaseUrl 开头（例如 http://127.0.0.1:8105/static/<objectKey>），去掉前缀后即为 objectKey。
     *  - 非 HTTP 的纯 objectKey 直接返回。
     */
    private String extractObjectKey(String url, String baseUrl) {
        if (!StringUtils.hasText(url)) return null;
        String s = url.trim();
        // /static/... 绝对路径模式
        if (s.startsWith("/static/")) return s.substring("/static/".length());
        // http(s)://host:port/static/...
        int idx = s.indexOf("/static/");
        if (idx >= 0) return s.substring(idx + "/static/".length());
        if (baseUrl != null && s.toLowerCase().startsWith(baseUrl.toLowerCase())) {
            String key = s.substring(baseUrl.length());
            if (key.startsWith("/")) key = key.substring(1);
            return key;
        }
        // minio 或直接 key
        return s;
    }

    private long guessFileSize(String objectKey) {
        try {
            if ("local".equalsIgnoreCase(storageProperties.getType())) {
                String base = storageProperties.getLocalBasePath();
                File f = new File(base, objectKey);
                if (f.exists() && f.isFile()) return f.length();
            }
        } catch (Exception ignored) {}
        return 0L;
    }

    private static String resolveMimeType(String extHint, String objectKey) {
        String ext = extHint;
        if (!StringUtils.hasText(ext) && objectKey != null && objectKey.contains(".")) {
            ext = objectKey.substring(objectKey.lastIndexOf('.') + 1);
        }
        if (!StringUtils.hasText(ext)) return "application/octet-stream";
        String e = ext.toLowerCase(Locale.ROOT);
        switch (e) {
            case "png": return "image/png";
            case "jpg": case "jpeg": return "image/jpeg";
            case "gif": return "image/gif";
            case "webp": return "image/webp";
            case "mp3": return "audio/mpeg";
            case "wav": return "audio/wav";
            case "aac": return "audio/aac";
            case "mp4": return "video/mp4";
            case "webm": return "video/webm";
            case "mov": return "video/quicktime";
            case "zip": return "application/zip";
            case "pdf": return "application/pdf";
            default: return "application/octet-stream";
        }
    }

    /** 文件类型：1图片 2音频 3视频 4文档 5其他 */
    private Integer detectFileType(String contentType, String objectKey) {
        if (StringUtils.hasText(contentType)) {
            if (contentType.startsWith("image/")) return 1;
            if (contentType.startsWith("audio/")) return 2;
            if (contentType.startsWith("video/")) return 3;
            if (contentType.contains("pdf") || contentType.contains("word") || contentType.contains("excel")
                    || contentType.contains("text") || contentType.contains("json") || contentType.contains("xml")) {
                return 4;
            }
        }
        if (StringUtils.hasText(objectKey)) {
            String ok = objectKey.toLowerCase(Locale.ROOT);
            if (ok.endsWith(".png") || ok.endsWith(".jpg") || ok.endsWith(".jpeg")
                    || ok.endsWith(".gif") || ok.endsWith(".webp")) return 1;
            if (ok.endsWith(".mp3") || ok.endsWith(".wav") || ok.endsWith(".aac")
                    || ok.endsWith(".m4a")) return 2;
            if (ok.endsWith(".mp4") || ok.endsWith(".webm") || ok.endsWith(".mov")
                    || ok.endsWith(".mkv")) return 3;
            if (ok.endsWith(".zip") || ok.endsWith(".pdf") || ok.endsWith(".doc")
                    || ok.endsWith(".txt") || ok.endsWith(".json") || ok.endsWith(".csv")) return 4;
        }
        return 5;
    }

    private String resolveBucket() {
        return "minio".equalsIgnoreCase(storageProperties.getType())
                ? storageProperties.getMinio().getBucket()
                : "local-storage";
    }

    private static String asStr(Object o) { return o == null ? null : String.valueOf(o); }
    private static Long asLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(o)); } catch (Exception ignored) { return null; }
    }
}
