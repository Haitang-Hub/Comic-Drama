package com.comicdrama.common.ai;

import java.util.function.Consumer;

/**
 * 流式 AI 模型调用接口（P2 扩展）。
 * 继承 {@link AiModelInvoker}，仍可注册到 InvokerRegistry。
 * 调用方通过 {@code invoker instanceof StreamingAiModelInvoker} 判断是否支持流式。
 *
 * 文本步骤（故事摘要/分镜脚本/资产设计）当模型支持 STREAMING 能力时，
 * 可走流式调用，chunk 通过 WebSocket 实时推送到前端，实现打字机效果。
 */
public interface StreamingAiModelInvoker extends AiModelInvoker {

    /**
     * 流式调用模型，每收到一段文本增量回调 chunkConsumer。
     *
     * @param context       模型上下文（含协议、API 地址、密钥等）
     * @param request       调用请求（含 prompt、参数等）
     * @param chunkConsumer 每收到一段增量文本回调（仅增量内容，非完整文本）
     * @return 聚合所有 chunk 后的完整响应（与 {@link #invoke} 返回结构一致，便于统一记录 Token 用量）
     */
    AiInvokeResponse invokeStream(AiModelContext context,
                                  AiInvokeRequest request,
                                  Consumer<String> chunkConsumer);
}
