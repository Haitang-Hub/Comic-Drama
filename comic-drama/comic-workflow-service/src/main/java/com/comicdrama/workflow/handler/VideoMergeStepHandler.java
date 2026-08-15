package com.comicdrama.workflow.handler;

import com.comicdrama.common.broadcast.MessageBroadcaster;
import com.comicdrama.common.dto.TaskCreateDTO;
import com.comicdrama.common.exception.BizException;
import com.comicdrama.common.result.Result;
import com.comicdrama.common.service.TaskPauseChecker;
import com.comicdrama.common.storage.StorageService;
import com.comicdrama.workflow.dto.FinalWorkInfo;
import com.comicdrama.workflow.dto.VideoManifestDTO;
import com.comicdrama.workflow.entity.SceneVideo;
import com.comicdrama.workflow.service.SceneVideoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * VIDEO_MERGE 步骤处理器：视频合并（步骤9）。
 * 纯算法处理步骤，不调用 AI 模型。
 *
 * <p>播放清单方案：收集所有场景视频 → 按序编号下载(001/002/003.mp4) →
 * 生成 manifest.json → 打包 ZIP → 上传存储 → 创建 ComicWork → 存 FinalWorkInfo 到 context。</p>
 */
@Slf4j
@Component
public class VideoMergeStepHandler extends AbstractStepHandler {

    private final SceneVideoService videoService;
    private final StorageService storageService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.resource-service.url:http://127.0.0.1:8105}")
    private String resourceServiceUrl;

    @Value("${app.video-merge.zip-url-expire-seconds:604800}")
    private int zipUrlExpireSeconds;

    @Value("${app.video-merge.download-timeout-seconds:300}")
    private int downloadTimeoutSeconds;

    public VideoMergeStepHandler(List<com.comicdrama.common.ai.AiModelInvoker> invokers,
                                 AiModelConfigProvider modelConfigProvider,
                                 PromptTemplateProvider promptTemplateProvider,
                                 TaskProgressRecorder progressRecorder,
                                 TaskFailureRecorder failureRecorder,
                                 MessageBroadcaster broadcaster,
                                 StepModelBindingResolver bindingResolver,
                                 TokenUsageRecorder tokenUsageRecorder,
                                 TaskPauseChecker pauseChecker,
                                 SceneVideoService videoService,
                                 StorageService storageService,
                                 RestTemplate restTemplate,
                                 ObjectMapper objectMapper) {
        super(invokers, modelConfigProvider, promptTemplateProvider, progressRecorder, failureRecorder,
                broadcaster, bindingResolver, tokenUsageRecorder, pauseChecker);
        this.videoService = videoService;
        this.storageService = storageService;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public StepEnum getStep() {
        return StepEnum.VIDEO_MERGE;
    }

    @Override
    protected void preCheck(StepContext context) {
        List<SceneVideo> videos = context.getArtifact(StepEnum.VIDEO);
        if (videos == null || videos.isEmpty()) {
            throw new BizException("前置步骤[VIDEO]产物缺失，无法进行视频合并");
        }
    }

    @Override
    protected void doExecute(StepContext context) throws Exception {
        Long taskId = context.getTaskId();
        List<SceneVideo> videos = context.getArtifact(StepEnum.VIDEO);

        log.info("[VIDEO_MERGE] 开始视频合并（播放清单方案），sceneVideoCount={}, taskId={}",
                videos.size(), taskId);
        reportProgress(context, 5, "正在收集场景视频...");

        // 1. 按 sceneGroupId + 起始分镜序号（从storyboardIds解析）双层严格排序，同组内按分镜号递增
        //    SceneVideo.sceneGroupId 可能重复，storyboardIds 格式为 "minSeq,maxSeq"（如 "1,2"）
        Comparator<SceneVideo> sceneOrder = Comparator
                .comparing((SceneVideo v) -> v.getSceneGroupId() == null ? 0L : v.getSceneGroupId())
                .thenComparingLong(v -> parseStartSeq(v.getStoryboardIds()))
                .thenComparing(v -> v.getId() == null ? 0L : v.getId());
        List<SceneVideo> sortedVideos = videos.stream()
                .filter(v -> StringUtils.hasText(v.getVideoUrl()))
                .sorted(sceneOrder)
                .collect(Collectors.toList());
        if (sortedVideos.isEmpty()) {
            throw new BizException("没有有效的场景视频可供合并");
        }

        // 2. 创建临时目录
        Path tempDir = Files.createTempDirectory("comic-merge-" + taskId + "-");
        log.info("[VIDEO_MERGE] 临时目录: {}, taskId={}", tempDir, taskId);

        try {
            // 3. 下载视频，按序编号 001_xxx.mp4, 002_xxx.mp4...
            reportProgress(context, 15, "正在下载场景视频...");
            List<DownloadedVideo> downloaded = downloadVideos(sortedVideos, tempDir, context);

            // 4. 生成 manifest.json
            reportProgress(context, 60, "正在生成播放清单...");
            VideoManifestDTO manifest = buildManifest(context, sortedVideos, downloaded);
            String manifestJson = objectMapper.writeValueAsString(manifest);
            Path manifestPath = tempDir.resolve("manifest.json");
            Files.writeString(manifestPath, manifestJson, StandardCharsets.UTF_8);

            // 5. 打包 ZIP
            reportProgress(context, 70, "正在打包ZIP文件...");
            String zipFileName = "comic-" + (context.getTaskNo() != null ? context.getTaskNo() : taskId) + ".zip";
            Path zipPath = tempDir.resolve(zipFileName);
            long zipSize = buildZip(downloaded, manifestPath, zipPath);

            // 6. 上传 ZIP 到 StorageService
            reportProgress(context, 85, "正在上传成片包...");
            String zipObjectKey = "task/" + taskId + "/final/" + zipFileName;
            try (InputStream zipIs = Files.newInputStream(zipPath)) {
                storageService.upload(zipIs, zipObjectKey, zipSize, "application/zip");
            }

            // 7. 获取签名URL
            String zipSignedUrl = storageService.signUrl(zipObjectKey, zipUrlExpireSeconds);

            // 8. 封面 = 首个场景视频的 baseFrameUrl
            String coverUrl = sortedVideos.get(0).getBaseFrameUrl();

            // 9. 调用 resource-service 创建/更新 ComicWork
            reportProgress(context, 90, "正在归档作品记录...");
            Long workId = upsertComicWork(context, coverUrl, zipSignedUrl,
                    manifest.getSegmentCount(), manifest.getTotalDuration(), sortedVideos);

            // 9.5 写入作品时间线条目（供详情页播放）
            if (workId != null) {
                writeTimeline(workId, sortedVideos);
            }

            // 10. 构建 FinalWorkInfo 存入 context
            FinalWorkInfo finalInfo = FinalWorkInfo.builder()
                    .coverUrl(coverUrl)
                    .finalVideoUrl(zipSignedUrl)
                    .zipObjectKey(zipObjectKey)
                    .zipFileSize(zipSize)
                    .segmentCount(manifest.getSegmentCount())
                    .totalDuration(manifest.getTotalDuration())
                    .workId(workId)
                    .videos(manifest.getVideos())
                    .manifestJson(manifestJson)
                    .build();
            context.putArtifact(StepEnum.VIDEO_MERGE, finalInfo);

            reportProgress(context, 100, "视频合并完成，成片包已生成");
            log.info("[VIDEO_MERGE] 完成 taskId={}, zipUrl={}, workId={}, size={}B, segments={}",
                    taskId, zipSignedUrl, workId, zipSize, manifest.getSegmentCount());

        } finally {
            cleanupTempDir(tempDir);
        }
    }

    // ==================== 下载视频 ====================

    private record DownloadedVideo(int orderIndex, String filename, Path path, SceneVideo source) {}

    private List<DownloadedVideo> downloadVideos(List<SceneVideo> videos, Path tempDir,
                                                  StepContext context) throws Exception {
        List<DownloadedVideo> result = new ArrayList<>();
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        int total = videos.size();
        for (int i = 0; i < total; i++) {
            SceneVideo v = videos.get(i);
            int orderIndex = i + 1;
            String filename = String.format("%03d_scene%d.mp4", orderIndex,
                    v.getSceneGroupId() != null ? v.getSceneGroupId() : orderIndex);
            Path target = tempDir.resolve(filename);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(v.getVideoUrl()))
                    .timeout(Duration.ofSeconds(downloadTimeoutSeconds))
                    .GET().build();
            HttpResponse<InputStream> resp = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() != 200) {
                throw new BizException("下载场景视频失败: HTTP " + resp.statusCode()
                        + ", url=" + v.getVideoUrl());
            }
            try (InputStream is = resp.body()) {
                Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
            }
            result.add(new DownloadedVideo(orderIndex, filename, target, v));

            int progress = 15 + (int) ((i + 1) / (double) total * 40);
            reportProgress(context, progress, "已下载 " + (i + 1) + "/" + total + " 段视频");
            log.debug("[VIDEO_MERGE] 下载完成 {}/{}: {}", i + 1, total, filename);
        }
        return result;
    }

    // ==================== 构建 manifest ====================

    private VideoManifestDTO buildManifest(StepContext context, List<SceneVideo> videos,
                                            List<DownloadedVideo> downloaded) {
        TaskCreateDTO dto = context.getRequestDTO();
        VideoManifestDTO manifest = new VideoManifestDTO();
        manifest.setTaskId(context.getTaskId());
        manifest.setTaskNo(context.getTaskNo());
        manifest.setTitle(dto != null && StringUtils.hasText(dto.getTitle()) ? dto.getTitle() : "Untitled");
        manifest.setResolution(dto != null ? dto.getResolution() : "1080p");
        manifest.setAspectRatio(dto != null ? dto.getAspectRatio() : "16:9");
        manifest.setCreatedAt(LocalDateTime.now().toString());
        manifest.setSegmentCount(videos.size());

        int totalDur = videos.stream()
                .mapToInt(v -> v.getDuration() != null ? v.getDuration().intValue() : 0)
                .sum();
        manifest.setTotalDuration(totalDur);

        List<VideoManifestDTO.VideoEntry> entries = new ArrayList<>();
        for (DownloadedVideo dv : downloaded) {
            VideoManifestDTO.VideoEntry e = new VideoManifestDTO.VideoEntry();
            e.setOrderIndex(dv.orderIndex());
            e.setFilename(dv.filename());
            e.setSceneGroupId(dv.source().getSceneGroupId());
            e.setStoryboardSeqRange(dv.source().getStoryboardSeqRange());
            e.setDuration(dv.source().getDuration() != null ? dv.source().getDuration().intValue() : 0);
            e.setOriginalUrl(dv.source().getVideoUrl());
            e.setCoverUrl(dv.source().getBaseFrameUrl());
            entries.add(e);
        }
        manifest.setVideos(entries);
        return manifest;
    }

    // ==================== 打包 ZIP ====================

    private long buildZip(List<DownloadedVideo> downloaded, Path manifestPath, Path zipPath) throws Exception {
        try (ZipOutputStream zos = new ZipOutputStream(
                new BufferedOutputStream(Files.newOutputStream(zipPath)))) {
            addToZip(zos, "manifest.json", manifestPath);
            for (DownloadedVideo dv : downloaded) {
                addToZip(zos, dv.filename(), dv.path());
            }
        }
        return Files.size(zipPath);
    }

    private void addToZip(ZipOutputStream zos, String entryName, Path file) throws Exception {
        zos.putNextEntry(new ZipEntry(entryName));
        try (InputStream is = Files.newInputStream(file)) {
            is.transferTo(zos);
        }
        zos.closeEntry();
    }

    // ==================== 创建/更新 ComicWork ====================

    @SuppressWarnings("unchecked")
    private Long upsertComicWork(StepContext context, String coverUrl, String zipUrl,
                                  int segmentCount, int duration, List<SceneVideo> sortedVideos) {
        Long taskId = context.getTaskId();
        Long userId = context.getUserId();
        String title = context.getRequestDTO() != null && StringUtils.hasText(context.getRequestDTO().getTitle())
                ? context.getRequestDTO().getTitle() : "Untitled";
        String resolution = context.getRequestDTO() != null ? context.getRequestDTO().getResolution() : null;
        String primaryVideoUrl = !sortedVideos.isEmpty() ? sortedVideos.get(0).getVideoUrl() : zipUrl;
        Long fileSize = (context.getArtifact(StepEnum.VIDEO_MERGE) instanceof FinalWorkInfo fwi) ? fwi.getZipFileSize() : null;

        // 1. 查是否已有 ComicWork
        try {
            String getUrl = resourceServiceUrl + "/api/work/task/" + taskId;
            ResponseEntity<Result<Map<String, Object>>> getResp = restTemplate.exchange(
                    getUrl, org.springframework.http.HttpMethod.GET, null,
                    new ParameterizedTypeReference<>() {});
            Map<String, Object> existing = extractData(getResp.getBody());
            if (existing != null && existing.get("id") != null) {
                // 已有 → PUT 更新
                Map<String, Object> body = new HashMap<>(existing);
                body.put("coverUrl", coverUrl);
                // 存首段可播放视频 URL（mp4），ZIP 包地址通过 manifest 下载
                body.put("videoUrl", primaryVideoUrl);
                body.put("duration", duration);
                body.put("segmentCount", segmentCount);
                if (resolution != null) body.put("resolution", resolution);
                restTemplate.put(resourceServiceUrl + "/api/work", body);
                log.info("[VIDEO_MERGE] ComicWork 已更新 workId={}, taskId={}",
                        existing.get("id"), taskId);
                return ((Number) existing.get("id")).longValue();
            }
        } catch (Exception e) {
            log.warn("[VIDEO_MERGE] 查询已有 ComicWork 失败（可能不存在），继续创建: {}", e.getMessage());
        }

        // 2. 不存在 → POST 创建（使用JSON请求体，避免URL编码导致中文标题存储错误）
        try {
            Map<String, Object> createBody = new HashMap<>();
            createBody.put("taskId", taskId);
            createBody.put("title", title);
            createBody.put("coverUrl", coverUrl != null ? coverUrl : "");
            createBody.put("finalVideoUrl", zipUrl);
            createBody.put("primaryVideoUrl", primaryVideoUrl);
            createBody.put("resolution", resolution != null ? resolution : "");
            createBody.put("duration", duration);
            createBody.put("userId", userId != null ? userId : 1L);

            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            org.springframework.http.HttpEntity<Map<String, Object>> requestEntity =
                    new org.springframework.http.HttpEntity<>(createBody, headers);

            ResponseEntity<Result<Map<String, Object>>> createResp = restTemplate.exchange(
                    resourceServiceUrl + "/api/work/create",
                    org.springframework.http.HttpMethod.POST, requestEntity,
                    new ParameterizedTypeReference<>() {});
            Map<String, Object> created = extractData(createResp.getBody());
            if (created != null && created.get("id") != null) {
                log.info("[VIDEO_MERGE] ComicWork 创建成功 workId={}, taskId={}, title={}",
                        created.get("id"), taskId, title);
                return ((Number) created.get("id")).longValue();
            }
        } catch (Exception e) {
            log.error("[VIDEO_MERGE] ComicWork 创建失败: {}", e.getMessage());
        }
        log.warn("[VIDEO_MERGE] ComicWork 创建响应异常，taskId={}", taskId);
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractData(Result<?> result) {
        if (result != null && result.getCode() == 200 && result.getData() != null) {
            return (Map<String, Object>) result.getData();
        }
        return null;
    }

    // ==================== 写入作品时间线 ====================

    private void writeTimeline(Long workId, List<SceneVideo> sortedVideos) {
        try {
            // 先清理旧的时间线条目（重新归档时）
            String listUrl = resourceServiceUrl + "/api/work/timeline/" + workId;
            try {
                ResponseEntity<Result<List<Map<String, Object>>>> listResp = restTemplate.exchange(
                        listUrl, org.springframework.http.HttpMethod.GET, null,
                        new ParameterizedTypeReference<>() {});
                List<Map<String, Object>> oldList = extractList(listResp.getBody());
                if (oldList != null && !oldList.isEmpty()) {
                    for (Map<String, Object> old : oldList) {
                        Object id = old.get("id");
                        if (id != null) {
                            try {
                                restTemplate.delete(resourceServiceUrl + "/api/work/timeline/" + id);
                            } catch (Exception ignored) {}
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("[VIDEO_MERGE] 清理旧时间线条目失败: {}", e.getMessage());
            }

            int orderIndex = 0;
            for (SceneVideo sv : sortedVideos) {
                try {
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("workId", workId);
                    payload.put("sceneGroupId", sv.getSceneGroupId());
                    payload.put("videoUrl", sv.getVideoUrl());
                    int orderIndex2 = orderIndex++;
                    payload.put("orderIndex", orderIndex2);
                    Integer durationSeconds = null;
                    if (sv.getDuration() != null && sv.getDuration().compareTo(java.math.BigDecimal.ZERO) > 0) {
                        durationSeconds = sv.getDuration().setScale(0, java.math.RoundingMode.CEILING).intValue();
                    }
                    payload.put("duration", durationSeconds);

                    org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
                    headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
                    org.springframework.http.HttpEntity<Map<String, Object>> entity =
                            new org.springframework.http.HttpEntity<>(payload, headers);

                    restTemplate.exchange(resourceServiceUrl + "/api/work/timeline",
                            org.springframework.http.HttpMethod.POST, entity,
                            new ParameterizedTypeReference<Result<Object>>() {});
                } catch (Exception e) {
                    log.warn("[VIDEO_MERGE] 写入时间线条目失败, workId={}, sceneGroupId={}: {}",
                            workId, sv.getSceneGroupId(), e.getMessage());
                }
            }
            log.info("[VIDEO_MERGE] 作品时间线写入完成 workId={}, timelineCount={}", workId, sortedVideos.size());
        } catch (Exception e) {
            log.error("[VIDEO_MERGE] 写入作品时间线异常: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> extractList(Result<?> result) {
        if (result != null && result.getCode() == 200 && result.getData() instanceof List) {
            return (List<T>) result.getData();
        }
        return null;
    }

    // ==================== 排序辅助 ====================

    /**
     * 解析 storyboardIds 字段的起始分镜序号。
     * 格式约定为 "minSeq,maxSeq"（如 "1,2"）；若为空或解析失败返回 Long.MAX_VALUE，
     * 保证缺值的条目排在同组末尾，不会乱插到前面。
     */
    private static long parseStartSeq(String storyboardIds) {
        if (!StringUtils.hasText(storyboardIds)) return Long.MAX_VALUE;
        try {
            int comma = storyboardIds.indexOf(',');
            String first = (comma > 0) ? storyboardIds.substring(0, comma) : storyboardIds;
            return Long.parseLong(first.trim());
        } catch (Exception e) {
            return Long.MAX_VALUE;
        }
    }

    // ==================== 清理临时文件 ====================

    private void cleanupTempDir(Path tempDir) {
        try {
            if (Files.exists(tempDir)) {
                try (Stream<Path> walk = Files.walk(tempDir)) {
                    walk.sorted(Comparator.reverseOrder())
                            .forEach(p -> {
                                try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                            });
                }
                log.debug("[VIDEO_MERGE] 临时目录已清理: {}", tempDir);
            }
        } catch (Exception e) {
            log.warn("[VIDEO_MERGE] 清理临时目录失败: {}, error={}", tempDir, e.getMessage());
        }
    }
}
