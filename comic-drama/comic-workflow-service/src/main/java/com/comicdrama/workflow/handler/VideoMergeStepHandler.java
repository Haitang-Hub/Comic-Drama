package com.comicdrama.workflow.handler;

import com.comicdrama.common.broadcast.MessageBroadcaster;
import com.comicdrama.common.dto.TaskCreateDTO;
import com.comicdrama.common.exception.BizException;
import com.comicdrama.common.result.Result;
import com.comicdrama.common.service.TaskPauseChecker;
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

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * VIDEO_MERGE 步骤处理器：视频合并（步骤9）。
 * 纯算法处理步骤，不调用 AI 模型。
 *
 * <p>懒打包方案：步骤9不再立即打包 ZIP，只做：
 * 收集所有场景视频 → 按序生成 manifest.json → 创建/更新 ComicWork 作品记录 →
 * 写入作品时间线 → 将 manifest 和打包元信息存 FinalWorkInfo 到 context。
 * 实际 ZIP 打包在用户点击「下载成片包」时按需生成（见 resource-service downloadZip 接口）。</p>
 */
@Slf4j
@Component
public class VideoMergeStepHandler extends AbstractStepHandler {

    private final SceneVideoService videoService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.resource-service.url:http://127.0.0.1:8105}")
    private String resourceServiceUrl;

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
                                 RestTemplate restTemplate,
                                 ObjectMapper objectMapper) {
        super(invokers, modelConfigProvider, promptTemplateProvider, progressRecorder, failureRecorder,
                broadcaster, bindingResolver, tokenUsageRecorder, pauseChecker);
        this.videoService = videoService;
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

        log.info("[VIDEO_MERGE] 开始视频合并（懒打包方案：本步不打包ZIP，仅归档manifest和作品），sceneVideoCount={}, taskId={}",
                videos.size(), taskId);
        reportProgress(context, 10, "正在收集场景视频...");

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

        reportProgress(context, 30, "正在生成播放清单...");

        // 2. 生成 manifest.json（用于在线播放 + 后续按需打包ZIP时复用）
        VideoManifestDTO manifest = buildManifest(context, sortedVideos);
        String manifestJson = objectMapper.writeValueAsString(manifest);

        // 3. 封面 = 首个场景视频的 baseFrameUrl
        String coverUrl = sortedVideos.get(0).getBaseFrameUrl();

        // 4. 调用 resource-service 创建/更新 ComicWork
        reportProgress(context, 60, "正在归档作品记录...");
        // 首段可播放视频 URL（mp4），ZIP 包地址留空，用户点击下载时按需生成
        String primaryVideoUrl = !sortedVideos.isEmpty() ? sortedVideos.get(0).getVideoUrl() : null;
        Long workId = upsertComicWork(context, coverUrl, null,
                manifest.getSegmentCount(), manifest.getTotalDuration(), sortedVideos);

        // 5. 写入作品时间线条目（供详情页播放）
        reportProgress(context, 80, "正在写入作品播放时间线...");
        if (workId != null) {
            writeTimeline(workId, sortedVideos);
        }

        // 6. 构建 FinalWorkInfo 存入 context（注意 finalVideoUrl 和 zip 字段均为 null，
        //    后续 downloadZip 接口按需生成ZIP并回填 comic_task.final_video_url 与 comic_work.zip_url）
        FinalWorkInfo finalInfo = FinalWorkInfo.builder()
                .coverUrl(coverUrl)
                .finalVideoUrl(null)      // 懒打包：ZIP URL 留空
                .zipObjectKey(null)
                .zipFileSize(null)
                .segmentCount(manifest.getSegmentCount())
                .totalDuration(manifest.getTotalDuration())
                .workId(workId)
                .videos(manifest.getVideos())
                .manifestJson(manifestJson)
                .build();
        context.putArtifact(StepEnum.VIDEO_MERGE, finalInfo);

        reportProgress(context, 100, "作品归档完成，下载成片包时将按需打包ZIP");
        log.info("[VIDEO_MERGE] 完成（懒打包）taskId={}, workId={}, segments={}, 总时长={}s",
                taskId, workId, manifest.getSegmentCount(), manifest.getTotalDuration());
    }

    // ==================== 构建 manifest ====================

    /**
     * 构建播放清单 manifest。
     * 文件名按序编号 001_scene{groupId}.mp4，与按需打包ZIP时生成的文件名保持一致。
     */
    private VideoManifestDTO buildManifest(StepContext context, List<SceneVideo> videos) {
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
        for (int i = 0; i < videos.size(); i++) {
            SceneVideo v = videos.get(i);
            int orderIndex = i + 1;
            String filename = String.format("%03d_scene%d.mp4", orderIndex,
                    v.getSceneGroupId() != null ? v.getSceneGroupId() : orderIndex);
            VideoManifestDTO.VideoEntry e = new VideoManifestDTO.VideoEntry();
            e.setOrderIndex(orderIndex);
            e.setFilename(filename);
            e.setSceneGroupId(v.getSceneGroupId());
            e.setStoryboardSeqRange(v.getStoryboardSeqRange());
            e.setDuration(v.getDuration() != null ? v.getDuration().intValue() : 0);
            e.setOriginalUrl(v.getVideoUrl());
            e.setCoverUrl(v.getBaseFrameUrl());
            entries.add(e);
        }
        manifest.setVideos(entries);
        return manifest;
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
}
