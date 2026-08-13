package com.comicdrama.workflow.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 步骤9最终成片信息（存入 StepContext 的 VIDEO_MERGE artifact）。
 * 供 WorkflowPipelineServiceImpl 读取后调用 markAsDone 更新 ComicTask。
 */
@Data
@Builder
public class FinalWorkInfo {

    /** 封面URL（首个场景视频的 baseFrameUrl） */
    private String coverUrl;

    /** ZIP 包签名下载URL */
    private String finalVideoUrl;

    /** ZIP 在 StorageService 中的 objectKey（供未来重签） */
    private String zipObjectKey;

    /** ZIP 文件大小（字节） */
    private Long zipFileSize;

    /** 视频段数 */
    private Integer segmentCount;

    /** 总时长（秒） */
    private Integer totalDuration;

    /** ComicWork ID（resource-service 返回） */
    private Long workId;

    /** 视频片段列表（用于在线播放） */
    private List<VideoManifestDTO.VideoEntry> videos;

    /** 完整 manifest JSON（包含播放列表，持久化到 comic_task 表供前端读取） */
    private String manifestJson;
}
