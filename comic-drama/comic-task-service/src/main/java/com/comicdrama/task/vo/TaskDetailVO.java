package com.comicdrama.task.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 任务详情 VO（包含所有步骤产物数据）
 */
@Data
public class TaskDetailVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // ========== 任务基本信息 ==========
    private Long id;
    private String taskNo;
    private String title;
    private String storyRequirement;
    private Integer status;
    private String statusText;
    private Integer currentStep;
    private Integer progress;
    private Integer duration;
    private String aspectRatio;
    private String resolution;
    private Integer voiceEnabled;
    private Integer execMode;
    private Boolean pendingReview;
    private String artStyle;
    private String visualStyle;
    private Integer failureStep;
    private String failureReason;
    private String failureDetail;
    private String createTime;
    private String startTime;
    private String endTime;
    private Integer totalConsumeTime;

    // ========== 步骤1: 故事摘要 ==========
    private StoryOutlineVO outline;

    // ========== 步骤2: 分镜脚本 ==========
    private List<SceneGroupVO> sceneGroups;
    private List<StoryboardVO> storyboards;

    // ========== 步骤3: 资产设计 ==========
    private List<AssetDesignVO> assetDesigns;

    // ========== 步骤4: 资产绘图（首版资产图，base_image_id IS NULL） ==========
    private List<AssetImageVO> assetImages;

    // ========== 步骤5: 衍生绘图（衍生资产图，base_image_id IS NOT NULL） ==========
    private List<AssetImageVO> deriveImages;

    // ========== 步骤5.5: 素材提示词（若后续接入） ==========
    private List<MaterialPromptVO> materialPrompts;

    // ========== 步骤6: 分镜绘图 ==========
    private List<StoryboardImageVO> images;

    // ========== 步骤7: 配音 ==========
    private List<StoryboardAudioVO> audios;

    // ========== 步骤8: 视频 ==========
    private List<SceneVideoVO> videos;

    // ========== 进度/日志 ==========
    private List<TaskProgressLogVO> progressLogs;
    private List<TaskFailureLogVO> failureLogs;
    private List<TaskNodeStateVO> nodeStates;

    // ========== 内嵌 VO 类 ==========

    @Data
    public static class StoryOutlineVO implements Serializable {
        private Long id;
        private String outlineText;
        private String summary;
        private Integer wordCount;
        /** 正面提示词（步骤1 AI生成） */
        private String positivePrompt;
        /** 负面提示词（步骤1 AI生成） */
        private String negativePrompt;
        private String createTime;
    }

    @Data
    public static class SceneGroupVO implements Serializable {
        private Long id;
        private Integer groupIndex;
        private String title;
        private String description;
        private Integer sceneCount;
        private Integer duration;
        private String createTime;
    }

    @Data
    public static class StoryboardVO implements Serializable {
        private Long id;
        private Long sceneGroupId;
        private Integer sceneIndex;
        private Integer localSeq;
        private String shotType;
        private String location;
        private String timeOfDay;
        private String characters;
        private String action;
        private String dialogue;
        private String emotion;
        private String cameraAngle;
        private String cameraMovement;
        private String scene;
        private String props;
        private String soundEffect;
        private String bgm;
        private Integer duration;
        private String prompt;
        private String shotDesc;
        private String storyboardDesc;
        private String visualDesc;
        private String createTime;
    }

    @Data
    public static class AssetDesignVO implements Serializable {
        private Long id;
        private String assetType;
        private String assetName;
        private String baseAssetName;
        private String derivedFrom;
        private String assetDesc;
        private String resourceUrl;
        private Integer version;
        private String createTime;
    }

    @Data
    public static class AssetImageVO implements Serializable {
        private Long id;
        private Long assetId;
        private String assetType;
        private String assetName;
        private String imageUrl;
        private String thumbnailUrl;
        private Integer width;
        private Integer height;
        private String promptUsed;
        private Integer status;
        private String createTime;
    }

    @Data
    public static class MaterialPromptVO implements Serializable {
        private Long id;
        private Integer sceneIndex;
        private String promptType;
        private String promptText;
        private String negativePrompt;
        private String createTime;
    }

    @Data
    public static class StoryboardImageVO implements Serializable {
        private Long id;
        private Integer sceneIndex;
        private String imageUrl;
        private String thumbnailUrl;
        private Integer width;
        private Integer height;
        private Long seed;
        private String prompt;
        private String createTime;
    }

    @Data
    public static class StoryboardAudioVO implements Serializable {
        private Long id;
        private Integer sceneIndex;
        private String roleName;
        private String audioUrl;
        private Integer duration;
        private String voiceName;
        private Integer speed;
        private String emotion;
        private String createTime;
    }

    @Data
    public static class SceneVideoVO implements Serializable {
        private Long id;
        private Integer sceneIndex;
        private String videoUrl;
        private String coverUrl;
        private Integer duration;
        private String resolution;
        private Long fileSize;
        private String createTime;
    }

    @Data
    public static class TaskProgressLogVO implements Serializable {
        private String id;
        private Long taskId;
        private Integer step;
        private String stepName;
        private Integer progress;
        private Integer status;
        private String message;
        private String createTime;
    }

    @Data
    public static class TaskFailureLogVO implements Serializable {
        private String id;
        private Long taskId;
        private Integer step;
        private String stepName;
        private String nodeType;
        private String nodeKey;
        private String modelName;
        private String errorType;
        private String errorCode;
        private String errorMessage;
        private String errorStack;
        private String stackTrace;
        private Integer retryCount;
        private Integer resolved;
        private String createTime;
    }

    @Data
    public static class TaskNodeStateVO implements Serializable {
        private String id;
        private Long taskId;
        private String nodeCode;
        private String nodeName;
        private Integer step;
        private Integer status;
        private String startTime;
        private String endTime;
        private Integer duration;
        private Integer durationMs;
        private String inputPayload;
        private String outputPayload;
        private String inputSnapshot;
        private String outputSnapshot;
        private String errorMessage;
        private String errorMsg;
        private Integer retryCount;
        private Integer regenerateCount;
        private String createTime;
    }
}
