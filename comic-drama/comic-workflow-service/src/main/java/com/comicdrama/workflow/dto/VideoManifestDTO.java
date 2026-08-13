package com.comicdrama.workflow.dto;

import lombok.Data;

import java.util.List;

/**
 * 视频播放清单（打包进 ZIP 的 manifest.json）。
 * 描述成片包内所有视频的顺序、时长和来源信息。
 */
@Data
public class VideoManifestDTO {

    private Long taskId;
    private String taskNo;
    private String title;
    private Integer totalDuration;
    private Integer segmentCount;
    private String resolution;
    private String aspectRatio;
    private String createdAt;
    private List<VideoEntry> videos;

    @Data
    public static class VideoEntry {
        /** 播放顺序（从1开始） */
        private Integer orderIndex;
        /** ZIP内文件名，如 001_scene3.mp4 */
        private String filename;
        private Long sceneGroupId;
        /** 分镜序号范围，如 "1-3" */
        private String storyboardSeqRange;
        /** 时长（秒） */
        private Integer duration;
        /** 原始场景视频URL */
        private String originalUrl;
        /** 首帧图URL（封面） */
        private String coverUrl;
    }
}
