package com.comicdrama.common.ai;

import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * AI 调用统一请求（Phase-1 仅声明，Phase-2 由各 Invoker 解析参数）。
 */
@Data
@Builder
public class AiInvokeRequest {

    /** 任务ID（用于产物存储路径分组） */
    private Long taskId;

    /** 模型服务商（deepseek/seedream/seed_tts/doubao_video） */
    private String modelProvider;

    /** 业务节点标识（用于失败日志定位，如 storyboard_3_image） */
    private String nodeKey;

    /** 文本提示词（文本/图像/视频模型） */
    private String prompt;

    /** 系统提示词 */
    private String systemPrompt;

    /** 参考图 URL（图生图 / image-to-video 首帧） */
    private String referenceImageUrl;

    /** 合成文本（TTS） */
    private String text;

    /** 音色素材编码（TTS） */
    private String voiceMaterialCode;

    /** 附加参数（画风强度/相似度权重/帧承接强度等） */
    @Builder.Default
    private Map<String, Object> extra = new HashMap<>();
}
