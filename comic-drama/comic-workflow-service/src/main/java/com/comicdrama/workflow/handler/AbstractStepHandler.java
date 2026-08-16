package com.comicdrama.workflow.handler;

import com.comicdrama.common.ai.AiInvokeRequest;
import com.comicdrama.common.ai.AiInvokeResponse;
import com.comicdrama.common.ai.AiModelContext;
import com.comicdrama.common.ai.AiModelInvoker;
import com.comicdrama.common.broadcast.MessageBroadcaster;
import com.comicdrama.common.broadcast.event.TaskProgressEvent;
import com.comicdrama.common.constant.CacheConstants;
import com.comicdrama.common.enums.ModelType;
import com.comicdrama.common.exception.BizException;
import com.comicdrama.common.exception.TaskPausedException;
import com.comicdrama.common.service.TaskPauseChecker;
import com.comicdrama.workflow.ai.InvokerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 步骤处理器抽象基类（模板方法模式）。
 * 定义 9 步流水线每步的标准执行流程：
 * <pre>
 *   调用前检查 → 构建 AI 请求 → 调用 AI → 解析结果 → 保存产物 → 记录进度
 * </pre>
 *
 * <h3>Phase-5 增强：测试优先 + 批量模式</h3>
 * <p>对于需要批量处理的步骤（步骤 4-8），支持先执行一条测试产物，
 * 成功后再批量生成全部产物。支持暂停/恢复和断点续跑。</p>
 *
 * @see #executeBatchWithTestFirst
 */
@Slf4j
public abstract class AbstractStepHandler {

    private final List<AiModelInvoker> invokers;
    private final AiModelConfigProvider modelConfigProvider;
    private final PromptTemplateProvider promptTemplateProvider;
    private final TaskProgressRecorder progressRecorder;
    private final TaskFailureRecorder failureRecorder;
    private final MessageBroadcaster broadcaster;
    private final StepModelBindingResolver bindingResolver;
    private final TokenUsageRecorder tokenUsageRecorder;
    private final TaskPauseChecker pauseChecker;

    /** Invoker 注册表（Spring 字段注入，避免修改所有子类构造函数） */
    @Autowired
    private InvokerRegistry invokerRegistry;

    /** 模型负载均衡选择器工厂（Spring 字段注入） */
    @Autowired
    private com.comicdrama.workflow.ai.selector.ModelSelectorFactory modelSelectorFactory;

    protected AbstractStepHandler(List<AiModelInvoker> invokers,
                                  AiModelConfigProvider modelConfigProvider,
                                  PromptTemplateProvider promptTemplateProvider,
                                  TaskProgressRecorder progressRecorder,
                                  TaskFailureRecorder failureRecorder,
                                  MessageBroadcaster broadcaster,
                                  StepModelBindingResolver bindingResolver,
                                  TokenUsageRecorder tokenUsageRecorder,
                                  TaskPauseChecker pauseChecker) {
        this.invokers = invokers;
        this.modelConfigProvider = modelConfigProvider;
        this.promptTemplateProvider = promptTemplateProvider;
        this.progressRecorder = progressRecorder;
        this.failureRecorder = failureRecorder;
        this.broadcaster = broadcaster;
        this.bindingResolver = bindingResolver;
        this.tokenUsageRecorder = tokenUsageRecorder;
        this.pauseChecker = pauseChecker;
    }

    /**
     * 获取当前处理器对应的步骤枚举。
     */
    public abstract StepEnum getStep();

    /**
     * 是否为批量步骤（需要测试优先 + 批量模式）。
     * 默认：步骤 4-8（ASSET_IMAGE, ASSET_DERIVE, STORYBOARD_IMAGE, AUDIO, VIDEO）为批量步骤。
     */
    protected boolean isBatchStep() {
        StepEnum step = getStep();
        return step == StepEnum.ASSET_IMAGE
                || step == StepEnum.ASSET_DERIVE
                || step == StepEnum.STORYBOARD_IMAGE
                || step == StepEnum.AUDIO
                || step == StepEnum.VIDEO;
    }

    /**
     * 模板方法：执行单个步骤。
     * 对于批量步骤，采用测试优先 + 批量模式；
     * 对于文本步骤（1-3），直接执行。
     */
    public final void execute(StepContext context) {
        long stepStart = System.currentTimeMillis();
        StepEnum step = getStep();
        context.setCurrentStep(step);

        log.info("===== 步骤开始：{} (order={}, code={}), taskId={} =====",
                step.getName(), step.getOrder(), step.getCode(), context.getTaskId());

        try {
            preCheck(context);

            if (isBatchStep() && !context.isSkipTestBatch()) {
                // 批量步骤：测试优先 + 批量模式
                doBatchExecute(context);
            } else {
                // 非批量步骤：直接执行
                doExecute(context);
            }

            onSuccess(context, stepStart);

        } catch (TaskPausedException e) {
            log.info("===== 步骤暂停：{} (order={}, code={}), taskId={} =====",
                    step.getName(), step.getOrder(), step.getCode(), context.getTaskId());
            // 暂停时不标记为失败，直接向上抛出由工作流引擎处理
            throw e;

        } catch (Exception e) {
            onFailure(context, stepStart, e);
            throw e instanceof BizException
                    ? (BizException) e
                    : new BizException("步骤[" + step.getName() + "]执行失败：" + e.getMessage());
        }
    }

    /**
     * 调用前检查：验证前置步骤产物是否就绪。
     * 默认实现空操作，子类按需覆写。
     */
    protected void preCheck(StepContext context) {
    }

    /**
     * 子类实现具体步骤逻辑（用于非批量步骤或测试阶段）。
     */
    protected abstract void doExecute(StepContext context) throws Exception;

    // ==================== 暂停检查 ====================

    /**
     * 检查任务是否已暂停，若已暂停则抛出 TaskPausedException。
     * 批量步骤应在每个产物生成前调用此方法。
     */
    protected void checkPaused(StepContext context) {
        if (pauseChecker != null && pauseChecker.isPaused(context.getTaskId())) {
            StepEnum step = getStep();
            log.info("任务已暂停，步骤 {} 终止执行 taskId={}", step.getName(), context.getTaskId());
            throw new TaskPausedException(step.getOrder(),
                    "任务在步骤[" + step.getName() + "]被暂停");
        }
    }

    // ==================== 测试优先 + 批量模式 ====================

    /**
     * 批量执行框架：先测试一条，再批量执行剩余。
     * <p>
     * 执行流程：
     * <ol>
     *   <li>从 items 中获取测试项（第一个未完成的项）</li>
     *   <li>执行测试项，失败则中止</li>
     *   <li>逐项执行剩余项，每一项前检查暂停状态</li>
     *   <li>任一项目失败时，保留已成功的项，仅标记失败项</li>
     * </ol>
     *
     * @param context    步骤上下文
     * @param items      待处理的所有项目（如分镜列表、资产列表等）
     * @param startIndex 起始索引（断点续跑时跳过已完成的项）
     * @param processor  单项处理器：(item, index) -> result，返回非 null 表示成功
     * @param onResult   结果回调：(item, index, result, success) 用于保存结果
     * @param <T>        项目类型
     * @param <R>        结果类型
     * @return 成功处理的项目数量
     */
    protected <T, R> int executeBatchWithTestFirst(
            StepContext context,
            List<T> items,
            int startIndex,
            java.util.function.BiFunction<T, Integer, R> processor,
            java.util.function.BiConsumer<T, R> onResult) throws Exception {

        StepEnum step = getStep();
        int total = items.size();
        int successCount = 0;
        int failedCount = 0;

        if (total == 0) {
            log.info("[{}] 无待处理项目，跳过", step.getName());
            return 0;
        }

        // === 第一阶段：测试（如果 startIndex == 0，即首次执行）===
        if (startIndex == 0) {
            int testIdx = 0;
            T testItem = items.get(testIdx);

            log.info("[{}] === 测试阶段：处理第 {}/{} 项 ===", step.getName(), testIdx + 1, total);
            reportProgress(context, 5, "进行中（1/" + total + "）", 1, total);

            R testResult;
            try {
                testResult = processor.apply(testItem, testIdx);
            } catch (Exception e) {
                log.error("[{}] 测试项执行失败：{}", step.getName(), e.getMessage());
                throw new BizException("[" + step.getCode() + "] 测试项执行失败：" + e.getMessage());
            }

            if (testResult == null) {
                log.error("[{}] 测试项返回空结果", step.getName());
                throw new BizException("[" + step.getCode() + "] 测试项生成失败，返回空结果");
            }

            onResult.accept(testItem, testResult);
            successCount++;
            log.info("[{}] 测试项成功，进入批量阶段", step.getName());
        }

        // === 第二阶段：批量 ===
        int batchStart = Math.max(startIndex, 1);

        for (int i = batchStart; i < total; i++) {
            checkPaused(context);

            T item = items.get(i);
            // 线性进度：已完成项占比映射到 5%~95% 区间
            // 测试项占总进度的 1/total（与其他项相同），不再独占 10%
            int progress = (int) (5 + ((double) (i) / total) * 90);
            reportProgress(context, progress,
                    "批量中（" + (i + 1) + "/" + total + "）", Math.min(i + 1, total), total);

            try {
                R result = processor.apply(item, i);
                if (result != null) {
                    onResult.accept(item, result);
                    successCount++;
                } else {
                    log.warn("[{}] 第 {}/{} 项返回空结果，跳过", step.getName(), i + 1, total);
                    failedCount++;
                }
            } catch (Exception e) {
                log.warn("[{}] 第 {}/{} 项失败：{}", step.getName(), i + 1, total, e.getMessage());
                failedCount++;
            }
        }

        // === 结果统计 ===
        int finalProgress = (int) ((successCount / (double) total) * 100);
        String summary;
        if (failedCount == 0) {
            summary = "已完成";
        } else if (successCount > 0) {
            summary = String.format("完成 %d/%d，%d 项失败", successCount, total, failedCount);
        } else {
            summary = String.format("全部失败 %d/%d", failedCount, total);
        }

        if (failedCount > 0 && successCount > 0) {
            reportProgress(context, Math.max(finalProgress, 10), summary, successCount, total);
        } else if (failedCount == total) {
            // 全部失败
            throw new BizException("[" + step.getCode() + "] 所有项目处理失败，无法继续执行后续步骤");
        }

        reportProgress(context, Math.max(finalProgress, 100), summary, successCount, total);
        log.info("[{}] {}，成功={}, 失败={}, total={}",
                step.getName(), summary, successCount, failedCount, total);

        return successCount;
    }

    /**
     * 批量步骤默认的 doExecute 实现：调用 executeBatchWithTestFirst。
     * 子类需实现 {@link #getBatchItems(StepContext)}、
     * {@link #processBatchItem(Object, int, StepContext)}、
     * {@link #saveBatchResult(Object, Object, StepContext)}。
     *
     * @return 成功处理的项目数量
     */
    protected int doBatchExecute(StepContext context) throws Exception {
        List<?> items = getBatchItems(context);
        if (items == null || items.isEmpty()) {
            log.info("[{}] 无待处理的批量项目", getStep().getName());
            return 0;
        }

        // 计算起始索引（断点续跑时跳过已完成的项）
        int startIndex = resolveBatchStartIndex(context, items);

        return executeBatchWithTestFirst(context, items, startIndex,
                (item, index) -> {
                    try {
                        return processBatchItem(item, index, context);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                },
                (item, result) -> saveBatchResult(item, result, context));
    }

    /**
     * 获取批量处理的项目列表。
     * 批量步骤子类需覆写；非批量步骤使用默认空列表。
     */
    protected <T> List<T> getBatchItems(StepContext context) {
        return java.util.Collections.emptyList();
    }

    /**
     * 处理单个批量项目。
     * 批量步骤子类需覆写；非批量步骤使用默认实现（抛出不支持异常）。
     */
    public <T, R> R processBatchItem(T item, int index, StepContext context) throws Exception {
        throw new UnsupportedOperationException("processBatchItem 未实现，非批量步骤不应调用此方法");
    }

    /**
     * 保存单个项目的处理结果。
     * 批量步骤子类需覆写；非批量步骤使用默认空实现。
     */
    public <T, R> void saveBatchResult(T item, R result, StepContext context) {
        // 非批量步骤默认空实现
    }

    /**
     * 计算批量处理的起始索引（用于断点续跑）。
     * 默认从 0 开始，子类可覆写以实现按ID精确匹配跳过已完成的项。
     *
     * @param context 步骤上下文
     * @param items    待处理的批量项目列表（用于逐项检查是否已完成）
     * @return 第一个未完成项的索引，若全部已完成则返回 items.size()
     */
    protected int resolveBatchStartIndex(StepContext context, List<?> items) {
        return 0;
    }

    // ==================== 进度上报 ====================

    /**
     * 按步骤进度记录（无 batch 子项）—— 同时推送 WebSocket 事件。
     */
    protected void reportProgress(StepContext context, int progress, String message) {
        reportProgress(context, progress, message, null, null);
    }

    /**
     * 按步骤进度记录（批量步骤专用，携带 itemDone/itemTotal 让前端显示 "3/8 已完成"）。
     */
    protected void reportProgress(StepContext context, int progress, String message,
                                  Integer itemDone, Integer itemTotal) {
        StepEnum step = getStep();
        int totalProgress = calculateTotalProgress(step, progress);

        context.setProgress(progress);
        context.setTotalProgress(totalProgress);

        progressRecorder.record(context.getTaskId(), step.getOrder(), step.getName(),
                progress, totalProgress, message, itemDone, itemTotal);

        log.debug("进度上报：step={}({}), progress={}, totalProgress={}, item={}/{}, taskId={}",
                step.getCode(), step.getName(), progress, totalProgress,
                itemDone == null ? "-" : itemDone,
                itemTotal == null ? "-" : itemTotal,
                context.getTaskId());
    }

    // ==================== AI 调用 ====================

    /**
     * 声明当前步骤要求的模型能力（默认无要求，子类按需覆写）。
     * <p>
     * 能力来源：Invoker 静态声明（{@link AiModelInvoker#capabilities()}）
     * + ai_model_config.capabilities JSON 配置，二者取并集后检查是否包含本方法返回的全部能力。
     * <p>
     * 示例：衍生绘图步骤要求 {@code IMAGE_TO_IMAGE}，视频生成步骤可要求 {@code FIRST_FRAME_LOCK}。
     *
     * @return 步骤要求的能力集合，空集合表示无特殊要求
     */
    protected java.util.Set<com.comicdrama.common.enums.ModelCapability> requiredCapabilities() {
        return java.util.Collections.emptySet();
    }

    /**
     * 检查已解析的模型是否具备步骤要求的全部能力。
     * <p>
     * 能力取并集：模型配置 capabilities（动态声明） + Invoker capabilities()（静态声明）。
     * 缺失时仅打印 WARN 日志（软校验，不阻断执行），便于旧模型向后兼容。
     *
     * @param modelContext 已解析的模型上下文
     * @param invoker      匹配到的 Invoker
     * @return true 表示能力满足或无要求；false 表示存在缺失（仍允许继续，调用方按需决定是否中止）
     */
    protected boolean modelSupports(AiModelContext modelContext, AiModelInvoker invoker) {
        java.util.Set<com.comicdrama.common.enums.ModelCapability> required = requiredCapabilities();
        if (required == null || required.isEmpty()) {
            return true;
        }

        // 能力并集：模型配置动态声明 + Invoker 静态声明
        java.util.Set<com.comicdrama.common.enums.ModelCapability> available = new java.util.HashSet<>();
        if (modelContext != null && modelContext.getCapabilities() != null) {
            available.addAll(modelContext.getCapabilities());
        }
        if (invoker != null && invoker.capabilities() != null) {
            available.addAll(invoker.capabilities());
        }

        java.util.Set<com.comicdrama.common.enums.ModelCapability> missing = new java.util.HashSet<>(required);
        missing.removeAll(available);

        if (!missing.isEmpty()) {
            log.warn("步骤[{}]模型能力不足：provider={}, model={}, 缺失能力={}, 已有能力={}",
                    getStep().getName(),
                    modelContext != null ? modelContext.getModelProvider() : "?",
                    modelContext != null ? modelContext.getModelName() : "?",
                    missing, available);
            return false;
        }
        return true;
    }

    /**
     * 通过模型服务商路由到对应 Invoker 并执行调用。
     */
    protected AiInvokeResponse invokeByModel(StepContext context, AiInvokeRequest request) {
        StepEnum step = getStep();
        Integer modelTypeCode = step.getModelType().getCode();

        AiModelContext modelContext = null;
        if (bindingResolver != null) {
            com.comicdrama.workflow.entity.AiModelConfig boundConfig = bindingResolver.resolveModelConfig(step);
            if (boundConfig != null) {
                modelContext = com.comicdrama.common.ai.AiModelContext.builder()
                        .id(boundConfig.getId())
                        .modelProvider(boundConfig.getModelProvider())
                        .modelName(boundConfig.getModelName())
                        .modelType(boundConfig.getModelType())
                        .protocol(boundConfig.getProtocol())
                        .capabilities(com.comicdrama.common.enums.ModelCapability.parseSet(boundConfig.getCapabilities()))
                        .selectorStrategy(boundConfig.getSelectorStrategy())
                        .weight(boundConfig.getWeight())
                        .apiUrl(boundConfig.getApiUrl())
                        .apiKey(boundConfig.getApiKey())
                        .status(boundConfig.getStatus())
                        .build();
                log.info("步骤[{}]使用绑定模型: provider={}, model={}",
                        step.getName(), boundConfig.getModelProvider(), boundConfig.getModelName());
            }
        }

        if (modelContext == null) {
            // 无步骤绑定 → 按模型类型获取所有候选，按负载均衡策略选择
            java.util.List<AiModelContext> candidates = modelConfigProvider.listByType(modelTypeCode);
            if (candidates == null || candidates.isEmpty()) {
                // 兜底：按步骤默认 provider 查找
                String defaultProvider = step.getModelProvider();
                modelContext = modelConfigProvider.getByProviderAndType(defaultProvider, modelTypeCode);
                if (modelContext == null) {
                    throw new BizException("AI 模型配置不存在：type=" + modelTypeCode
                            + "，请在系统设置 → AI模型配置中添加该类型的模型");
                }
            } else if (candidates.size() == 1) {
                // 仅一个候选，直接使用
                modelContext = candidates.get(0);
            } else {
                // 多候选 → 按策略负载均衡选择
                String strategy = candidates.get(0).getSelectorStrategy();
                modelContext = modelSelectorFactory.select(strategy, candidates);
                if (modelContext == null) {
                    modelContext = candidates.get(0);
                }
                log.info("步骤[{}]负载均衡选择：strategy={}, candidates={}, selected={}",
                        step.getName(), strategy, candidates.size(), modelContext.getModelName());
            }
        }

        AiModelInvoker invoker = findInvoker(ModelType.fromCode(modelContext.getModelType()),
                modelContext.getModelProvider(), modelContext.getProtocol());
        if (invoker == null) {
            throw new BizException("未找到匹配的 Invoker：modelType=" + modelContext.getModelType()
                    + ", modelProvider=" + modelContext.getModelProvider()
                    + ", protocol=" + modelContext.getProtocol());
        }

        // 能力校验（软校验：缺失时 WARN 但不阻断，兼容旧模型配置）
        modelSupports(modelContext, invoker);

        log.info("调用 AI 模型：protocol={}, provider={}, apiModel={}, type={}, nodeKey={}",
                modelContext.getProtocol(), modelContext.getModelProvider(), modelContext.resolveApiModel(),
                modelContext.getModelType(), request.getNodeKey());

        request.setTaskId(context.getTaskId());

        AiInvokeResponse response = invoker.invoke(modelContext, request);
        tokenUsageRecorder.record(context, step, modelContext, request, response);
        return response;
    }

    /**
     * 流式调用 AI 模型：当 Invoker 实现 {@link com.comicdrama.common.ai.StreamingAiModelInvoker}
     * 且模型声明 {@code STREAMING} 能力时，走 SSE 流式调用，增量 chunk 通过 broadcaster 推送到前端。
     * <p>
     * 若 Invoker 不支持流式，自动回退到 {@link #invokeByModel} 非流式调用。
     *
     * @param context 步骤上下文
     * @param request AI 调用请求
     * @return 聚合完整文本的响应（与非流式返回结构一致）
     */
    protected AiInvokeResponse invokeByModelStream(StepContext context, AiInvokeRequest request) {
        StepEnum step = getStep();
        Integer modelTypeCode = step.getModelType().getCode();

        // 解析模型上下文（与 invokeByModel 一致）
        AiModelContext modelContext = null;
        if (bindingResolver != null) {
            com.comicdrama.workflow.entity.AiModelConfig boundConfig = bindingResolver.resolveModelConfig(step);
            if (boundConfig != null) {
                modelContext = com.comicdrama.common.ai.AiModelContext.builder()
                        .id(boundConfig.getId())
                        .modelProvider(boundConfig.getModelProvider())
                        .modelName(boundConfig.getModelName())
                        .modelType(boundConfig.getModelType())
                        .protocol(boundConfig.getProtocol())
                        .capabilities(com.comicdrama.common.enums.ModelCapability.parseSet(boundConfig.getCapabilities()))
                        .selectorStrategy(boundConfig.getSelectorStrategy())
                        .weight(boundConfig.getWeight())
                        .apiUrl(boundConfig.getApiUrl())
                        .apiKey(boundConfig.getApiKey())
                        .status(boundConfig.getStatus())
                        .build();
            }
        }
        if (modelContext == null) {
            modelContext = modelConfigProvider.getByProviderAndType(step.getModelProvider(), modelTypeCode);
        }

        AiModelInvoker invoker = findInvoker(ModelType.fromCode(modelContext.getModelType()),
                modelContext.getModelProvider(), modelContext.getProtocol());

        // 不支持流式 → 回退到非流式
        if (!(invoker instanceof com.comicdrama.common.ai.StreamingAiModelInvoker)) {
            log.info("步骤[{}] Invoker 不支持流式，回退到非流式调用", step.getName());
            return invokeByModel(context, request);
        }

        // 模型未声明 STREAMING 能力 → 回退
        if (!modelContext.hasCapability(com.comicdrama.common.enums.ModelCapability.STREAMING)) {
            log.info("步骤[{}]模型未声明 STREAMING 能力，回退到非流式调用", step.getName());
            return invokeByModel(context, request);
        }

        com.comicdrama.common.ai.StreamingAiModelInvoker streamingInvoker =
                (com.comicdrama.common.ai.StreamingAiModelInvoker) invoker;

        log.info("步骤[{}]走流式调用：protocol={}, model={}", step.getName(),
                modelContext.getProtocol(), modelContext.getModelName());
        request.setTaskId(context.getTaskId());

        // chunkConsumer：通过 broadcaster 推送增量到前端 WebSocket
        String channel = "task_stream_" + context.getTaskId();
        AiInvokeResponse response = streamingInvoker.invokeStream(modelContext, request, chunk -> {
            if (broadcaster != null && chunk != null && !chunk.isEmpty()) {
                try {
                    broadcaster.publish(channel, java.util.Map.of(
                            "taskId", context.getTaskId(),
                            "step", step.getOrder(),
                            "nodeKey", request.getNodeKey() != null ? request.getNodeKey() : "",
                            "chunk", chunk
                    ));
                } catch (Exception e) {
                    log.debug("流式 chunk 推送失败：{}", e.getMessage());
                }
            }
        });

        tokenUsageRecorder.record(context, step, modelContext, request, response);
        return response;
    }

    /**
     * 按模板编码加载 Prompt 模板内容。
     */
    protected String loadPromptTemplate(String templateCode) {
        String content = promptTemplateProvider.getTemplateContent(templateCode);
        if (content == null || content.isEmpty()) {
            throw new BizException("Prompt 模板不存在：" + templateCode);
        }
        return content;
    }

    /**
     * 将模板中的 {{key}} 占位符替换为实际值。
     */
    protected String fillTemplate(String template, String... keyValues) {
        if (keyValues == null || keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("keyValues 必须是偶数个（key, value 成对）");
        }
        if (template == null) {
            return "";
        }
        String result = template;
        for (int i = 0; i < keyValues.length; i += 2) {
            String key = keyValues[i];
            String value = keyValues[i + 1];
            if (value == null) {
                value = "";
            }
            result = result.replace("{{" + key + "}}", value);
        }
        return result;
    }

    /**
     * 清理 AI 返回文本中的 Markdown 代码围栏，提取纯 JSON。
     */
    protected String cleanJsonText(String text) {
        if (text == null) return null;
        String cleaned = text.trim();

        if (cleaned.startsWith("```")) {
            int firstNewline = cleaned.indexOf('\n');
            if (firstNewline > 0) {
                cleaned = cleaned.substring(firstNewline + 1);
            }
            int lastFence = cleaned.lastIndexOf("```");
            if (lastFence >= 0) {
                cleaned = cleaned.substring(0, lastFence);
            }
            cleaned = cleaned.trim();
        }

        if (cleaned.contains("```")) {
            cleaned = cleaned.replaceAll("```[a-zA-Z0-9]*", "").replaceAll("```", "").trim();
        }

        return cleaned;
    }

    private static final Pattern MARKDOWN_CODE_BLOCK_PATTERN = Pattern.compile(
            "```[a-zA-Z0-9]*\\s*\\n?(.*?)\\n?\\s*```", Pattern.DOTALL);

    /**
     * 从 AI 返回文本中提取有效内容。
     */
    protected String extractOutputContent(String text) {
        if (text == null) return "";
        String trimmed = text.trim();

        Matcher mdMatcher = MARKDOWN_CODE_BLOCK_PATTERN.matcher(trimmed);
        if (mdMatcher.find()) {
            String codeBlockContent = mdMatcher.group(1).trim();
            log.debug("[extractContent] 匹配到 Markdown 代码块，长度 {}", codeBlockContent.length());
            return codeBlockContent;
        }

        String cleaned = cleanJsonText(trimmed);
        log.debug("[extractContent] 未匹配到代码块，使用清洗后全文，原始长度={}, 清洗后长度={}",
                trimmed.length(), cleaned.length());
        return cleaned;
    }

    /**
     * 获取当前步骤绑定的模型配置（可能为 null，表示使用默认/负载均衡）。
     * 供子类在构造请求前判断协议类型，按需走差异化参数装配路径。
     */
    protected com.comicdrama.workflow.entity.AiModelConfig getBoundModelConfig() {
        if (bindingResolver != null) {
            return bindingResolver.resolveModelConfig(getStep());
        }
        return null;
    }

    /**
     * 查找匹配的 Invoker（协议化路由）。
     * 路由优先级：
     * 1. 按 protocol+type 精确匹配（新路径，O(1) 注册表查询）
     * 2. 按 type+provider 走旧 supports 逻辑（fallback，兼容无 protocol 旧数据）
     * 3. 按 type 取第一个（最终兜底）
     */
    private AiModelInvoker findInvoker(ModelType modelType, String modelProvider, String protocol) {
        // 1. 优先按 protocol 路由（新路径）
        if (protocol != null && !protocol.isEmpty()) {
            AiModelInvoker invoker = invokerRegistry.get(protocol, modelType);
            if (invoker != null) {
                return invoker;
            }
            log.warn("protocol[{}]无匹配 Invoker，回退到 type+provider 路由", protocol);
        }
        // 2. Fallback：按 type+provider 走旧 supports 逻辑
        AiModelInvoker invoker = invokerRegistry.getByTypeAndProvider(modelType, modelProvider);
        if (invoker != null) {
            return invoker;
        }
        // 3. 最终 Fallback：按 type 取第一个
        List<AiModelInvoker> list = invokerRegistry.getByType(modelType);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 计算流水线总进度。
     */
    private int calculateTotalProgress(StepEnum step, int stepProgress) {
        int totalSteps = StepEnum.values().length;
        int completedSteps = step.getOrder() - 1;
        // 公式：(已完成步骤数 × 100 + 当前步骤内进度) / 总步骤数
        // 例：步骤3完成50% = (2×100 + 50) / 9 = 27%
        int totalProgress = (completedSteps * 100 + stepProgress) / totalSteps;
        return Math.min(totalProgress, 100);
    }

    /**
     * 成功回调。
     */
    private void onSuccess(StepContext context, long stepStart) {
        long costMs = System.currentTimeMillis() - stepStart;
        StepEnum step = getStep();
        reportProgress(context, 100, step.getName() + "完成");

        log.info("===== 步骤完成：{} (order={}, code={}), taskId={}, 耗时={}ms =====",
                step.getName(), step.getOrder(), step.getCode(), context.getTaskId(), costMs);
    }

    /**
     * 失败回调：记录失败日志 + 广播状态变更。
     */
    private void onFailure(StepContext context, long stepStart, Exception e) {
        long costMs = System.currentTimeMillis() - stepStart;
        StepEnum step = getStep();

        log.error("===== 步骤失败：{} (order={}, code={}), taskId={}, 耗时={}ms =====",
                step.getName(), step.getOrder(), step.getCode(), context.getTaskId(), costMs, e);

        failureRecorder.record(
                context.getTaskId(),
                step.getOrder(),
                step.getCode(),
                e.getMessage(),
                e
        );

        broadcaster.publish(CacheConstants.CHANNEL_TASK_STATUS,
                new com.comicdrama.common.broadcast.event.TaskStatusChangeEvent(
                        this, context.getTaskId(), 1, 3
                )
        );
    }
}
