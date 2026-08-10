package com.comicdrama.workflow.handler;

import com.comicdrama.common.ai.AiInvokeRequest;
import com.comicdrama.common.ai.AiInvokeResponse;
import com.comicdrama.common.broadcast.MessageBroadcaster;
import com.comicdrama.common.exception.BizException;
import com.comicdrama.common.service.TaskPauseChecker;
import com.comicdrama.workflow.entity.Storyboard;
import com.comicdrama.workflow.entity.StoryboardAudio;
import com.comicdrama.workflow.entity.AssetDesign;
import com.comicdrama.workflow.service.StoryboardAudioService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AUDIO 步骤处理器：角色音频生成（Seed-TTS）。
 * 为每个分镜生成角色配音，支持音色克隆。
 * 当 voiceEnabled=0 时自动跳过。
 *
 * <p>Phase-5：支持测试优先+批量模式，先测试一条，成功后批量生成。</p>
 */
@Slf4j
@Component
public class AudioStepHandler extends AbstractStepHandler {

    private final StoryboardAudioService audioService;
    private final ObjectMapper objectMapper;

    public AudioStepHandler(List<com.comicdrama.common.ai.AiModelInvoker> invokers,
                            AiModelConfigProvider modelConfigProvider,
                            PromptTemplateProvider promptTemplateProvider,
                            TaskProgressRecorder progressRecorder,
                            TaskFailureRecorder failureRecorder,
                            MessageBroadcaster broadcaster,
                            StepModelBindingResolver bindingResolver,
                            TokenUsageRecorder tokenUsageRecorder,
                            TaskPauseChecker pauseChecker,
                            StoryboardAudioService audioService,
                            ObjectMapper objectMapper) {
        super(invokers, modelConfigProvider, promptTemplateProvider, progressRecorder, failureRecorder,
                broadcaster, bindingResolver, tokenUsageRecorder, pauseChecker);
        this.audioService = audioService;
        this.objectMapper = objectMapper;
    }

    @Override
    public StepEnum getStep() {
        return StepEnum.AUDIO;
    }

    @Override
    protected void preCheck(StepContext context) {
        Integer voiceEnabled = context.getRequestDTO().getVoiceEnabled();
        if (voiceEnabled != null && voiceEnabled == 0) {
            log.info("[AUDIO] preCheck: 配音已关闭（voiceEnabled=0），跳过前置条件验证");
            return;
        }

        List<AssetDesign> assets = context.getArtifact(StepEnum.ASSET_DESIGN);
        List<Storyboard> storyboards = context.getArtifact(StepEnum.STORYBOARD);
        if (assets == null || assets.isEmpty()) {
            throw new BizException("前置步骤[ASSET_DESIGN]产物缺失，无法生成音频");
        }
        if (storyboards == null || storyboards.isEmpty()) {
            throw new BizException("前置步骤[STORYBOARD]产物缺失，无法生成音频");
        }
    }

    @Override
    protected void doExecute(StepContext context) throws Exception {
        Integer voiceEnabled = context.getRequestDTO().getVoiceEnabled();
        if (voiceEnabled != null && voiceEnabled == 0) {
            log.info("[AUDIO] 配音已关闭（voiceEnabled=0），跳过音频生成");
            reportProgress(context, 100, "配音已关闭，跳过");
            return;
        }

        List<AssetDesign> assets = context.getArtifact(StepEnum.ASSET_DESIGN);
        List<Storyboard> storyboards = context.getArtifact(StepEnum.STORYBOARD);

        List<AssetDesign> voiceAssets = assets.stream()
                .filter(a -> "音色".equals(a.getAssetType()))
                .toList();

        log.info("[AUDIO] 开始生成角色音频，storyboardCount={}, voiceAssetCount={}, taskId={}",
                storyboards.size(), voiceAssets.size(), context.getTaskId());

        context.putArtifact(StepEnum.AUDIO, new ArrayList<StoryboardAudio>());

        doBatchExecute(context);

        List<StoryboardAudio> audios = context.getArtifact(StepEnum.AUDIO);
        if (audios == null) {
            audios = new ArrayList<>();
        }

        // 音频已在 saveBatchResult 中逐条 upsert，无需批量保存
        log.info("[AUDIO] 角色音频upsert完成，totalCount={}, taskId={}", audios.size(), context.getTaskId());

        reportProgress(context, 100, "角色音频生成完成");
        log.info("[AUDIO] 角色音频生成成功，totalCount={}, taskId={}", audios.size(), context.getTaskId());
    }

    @Override
    @SuppressWarnings("unchecked")
    protected <T> List<T> getBatchItems(StepContext context) {
        List<Storyboard> storyboards = context.getArtifact(StepEnum.STORYBOARD);
        return (List<T>) storyboards;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T, R> R processBatchItem(T item, int index, StepContext context) throws Exception {
        Storyboard sb = (Storyboard) item;

        String dialogue = sb.getDialogue();
        if (!StringUtils.hasText(dialogue)) {
            log.debug("[AUDIO] 分镜{}无对白，跳过", sb.getSeq());
            return null;
        }

        List<AssetDesign> assets = context.getArtifact(StepEnum.ASSET_DESIGN);
        List<AssetDesign> voiceAssets = assets.stream()
                .filter(a -> "音色".equals(a.getAssetType()))
                .toList();

        Map<String, AssetDesign> voiceByCharacter = new HashMap<>();
        for (AssetDesign va : voiceAssets) {
            if (StringUtils.hasText(va.getAssetName())) {
                voiceByCharacter.put(va.getAssetName(), va);
            }
        }

        String voiceAssetName = resolveVoiceAssetName(sb, voiceByCharacter, voiceAssets);

        Map<String, Object> extra = new HashMap<>();
        extra.put("speed", 1.0);

        AiInvokeRequest request = AiInvokeRequest.builder()
                .modelProvider(getStep().getModelProvider())
                .nodeKey("audio_sb_" + sb.getSeq())
                .text(dialogue)
                .prompt(sb.getShotDesc())
                .extra(extra)
                .build();

        AiInvokeResponse response = invokeByModel(context, request);

        if (!response.isSuccess()) {
            log.warn("[AUDIO] 分镜{}音频生成失败: {}", sb.getSeq(), response.getErrorMessage());
            return null;
        }

        StoryboardAudio audio = createStoryboardAudio(response, context, sb, voiceAssetName, extra);

        log.info("[AUDIO] 分镜{}音频生成成功，audioUrl={}", sb.getSeq(), response.getResourceUrl());
        return (R) audio;
    }

    @Override
    protected int resolveBatchStartIndex(StepContext context, int totalSize) {
        long existingCount = audioService.lambdaQuery()
                .eq(StoryboardAudio::getTaskId, context.getTaskId())
                .count();
        return (int) Math.min(existingCount, totalSize);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T, R> void saveBatchResult(T item, R result, StepContext context) {
        if (result == null) return;

        List<StoryboardAudio> audios = context.getArtifact(StepEnum.AUDIO);
        if (audios == null) {
            audios = new ArrayList<>();
            context.putArtifact(StepEnum.AUDIO, audios);
        }

        StoryboardAudio audio = (StoryboardAudio) result;

        StoryboardAudio existed = audioService.lambdaQuery()
                .eq(StoryboardAudio::getTaskId, audio.getTaskId())
                .eq(StoryboardAudio::getStoryboardId, audio.getStoryboardId())
                .one();
        if (existed != null) {
            audio.setId(existed.getId());
            audioService.updateById(audio);
        } else {
            audioService.save(audio);
        }

        audios.add(audio);
    }

    private String resolveVoiceAssetName(Storyboard sb, Map<String, AssetDesign> voiceByCharacter,
                                         List<AssetDesign> voiceAssets) {
        if (StringUtils.hasText(sb.getCharacter())) {
            AssetDesign matched = voiceByCharacter.get(sb.getCharacter());
            if (matched != null && StringUtils.hasText(matched.getAssetName())) {
                return matched.getAssetName();
            }
        }
        if (!voiceAssets.isEmpty()) {
            return voiceAssets.get(0).getAssetName();
        }
        return null;
    }

    private StoryboardAudio createStoryboardAudio(AiInvokeResponse response, StepContext context,
                                                   Storyboard sb, String voiceAssetName,
                                                   Map<String, Object> extra) throws JsonProcessingException {
        StoryboardAudio audio = new StoryboardAudio();
        audio.setTaskId(context.getTaskId());
        audio.setStoryboardId(sb.getId());
        audio.setAudioUrl(response.getResourceUrl());
        audio.setText(sb.getDialogue());
        audio.setSpeed(50);
        audio.setDuration(BigDecimal.valueOf(sb.getDuration()));
        audio.setStatus(1);
        audio.setRegenerateCount(0);

        Map<String, Object> resExtra = response.getExtra();
        if (resExtra != null && resExtra.containsKey("duration")) {
            audio.setDuration(new BigDecimal(resExtra.get("duration").toString()));
        }

        return audio;
    }
}