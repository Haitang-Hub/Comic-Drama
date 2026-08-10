package com.comicdrama.workflow.handler;

import com.comicdrama.common.ai.AiInvokeRequest;
import com.comicdrama.common.ai.AiInvokeResponse;
import com.comicdrama.common.broadcast.MessageBroadcaster;
import com.comicdrama.common.exception.BizException;
import com.comicdrama.common.service.TaskPauseChecker;
import com.comicdrama.workflow.entity.AssetDesign;
import com.comicdrama.workflow.entity.StorySummary;
import com.comicdrama.workflow.entity.Storyboard;
import com.comicdrama.workflow.service.AssetDesignService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ASSET_DESIGN 步骤处理器：资产设计（步骤3）。
 * 根据分镜脚本生成人物/场景/道具/音色等资产脚本。
 * 格式：资产类型|资产名称（含版本标识）|基础资产名|衍生自|资产描述|版本
 */
@Slf4j
@Component
public class AssetDesignStepHandler extends AbstractStepHandler {

    private final AssetDesignService assetDesignService;

    public AssetDesignStepHandler(List<com.comicdrama.common.ai.AiModelInvoker> invokers,
                                  AiModelConfigProvider modelConfigProvider,
                                  PromptTemplateProvider promptTemplateProvider,
                                  TaskProgressRecorder progressRecorder,
                                  TaskFailureRecorder failureRecorder,
                                  MessageBroadcaster broadcaster,
                                  StepModelBindingResolver bindingResolver,
                                  TokenUsageRecorder tokenUsageRecorder,
                                  AssetDesignService assetDesignService,
                                  TaskPauseChecker pauseChecker) {
        super(invokers, modelConfigProvider, promptTemplateProvider, progressRecorder, failureRecorder, broadcaster, bindingResolver, tokenUsageRecorder, pauseChecker);
        this.assetDesignService = assetDesignService;
    }

    @Override
    public StepEnum getStep() {
        return StepEnum.ASSET_DESIGN;
    }

    @Override
    protected void preCheck(StepContext context) {
        StorySummary summary = context.getArtifact(StepEnum.SUMMARY);
        if (summary == null || summary.getContent() == null) {
            throw new BizException("[ASSET_DESIGN] 前置步骤[SUMMARY]产物缺失，无法进行资产设计");
        }
    }

    @Override
    protected void doExecute(StepContext context) throws Exception {
        log.info("[ASSET_DESIGN] 开始生成资产设计，taskId={}", context.getTaskId());

        reportProgress(context, 10, "正在加载 Prompt 模板...");

        String template = loadPromptTemplate("asset_design");
        
        // 获取分镜数据，并格式化为步骤2要求的|分隔CSV格式，便于AI理解
        List<Storyboard> storyboards = context.getArtifact(StepEnum.STORYBOARD);
        String storyboardText = formatStoryboardsForAssetDesign(storyboards);
        
        String filledPrompt = fillTemplate(template,
                "storyboards", storyboardText);

        reportProgress(context, 30, "正在调用 AI 生成资产设计...");

        AiInvokeRequest request = AiInvokeRequest.builder()
                .modelProvider(getStep().getModelProvider())
                .nodeKey("asset_design")
                .prompt(filledPrompt)
                .build();

        AiInvokeResponse response = invokeByModel(context, request);

        if (!response.isSuccess()) {
            throw new BizException("资产设计生成失败：" + response.getErrorMessage());
        }

        reportProgress(context, 70, "正在解析并保存资产设计结果...");

        List<AssetDesign> assets = parseAndSaveAssets(response.getText(), context);

        context.putArtifact(StepEnum.ASSET_DESIGN, assets);

        reportProgress(context, 100, "资产设计生成完成");
        log.info("[ASSET_DESIGN] 资产设计生成成功，assetCount={}, taskId={}", assets.size(), context.getTaskId());
    }

    /**
     * 步骤3要求：输入"分镜脚本"为|分隔的CSV格式。
     * 将 List<Storyboard> 重新序列化为可读 CSV（12列），便于 AI 提取人物/场景/道具/音色等。
     */
    private String formatStoryboardsForAssetDesign(List<Storyboard> storyboards) {
        if (storyboards == null || storyboards.isEmpty()) {
            return "";
        }
        List<Storyboard> sorted = storyboards.stream()
                .sorted((a, b) -> Integer.compare(a.getSeq(), b.getSeq()))
                .collect(Collectors.toList());
        StringBuilder sb = new StringBuilder();
        sb.append("分镜序号|本镜时长|场景分组ID|组内序号|镜头角度|镜头描述|场景|出场角色|出场道具|分镜描述|台词内容|画面描述\n");
        for (Storyboard s : sorted) {
            sb.append(nullSafe(s.getSeq())).append("|")
              .append(nullSafe(s.getDuration())).append("|")
              .append(nullSafe(s.getGroupId())).append("|")
              .append(nullSafe(s.getLocalSeq())).append("|")
              .append(nullSafe(s.getCameraAngle())).append("|")
              .append(escapePipe(s.getShotDesc())).append("|")
              .append(escapePipe(s.getScene())).append("|")
              .append(nullSafe(s.getCharacter())).append("|")
              .append(nullSafe(s.getProps())).append("|")
              .append(escapePipe(s.getStoryboardDesc())).append("|")
              .append(escapePipe(s.getDialogue())).append("|")
              .append(escapePipe(s.getVisualDesc())).append("\n");
        }
        return sb.toString();
    }

    private static String escapePipe(String s) {
        if (s == null) return "";
        // 把 | 转成全角以避免破坏CSV结构
        return s.replace("|", "｜");
    }

    private static String nullSafe(Object o) {
        return o == null ? "" : o.toString();
    }

    private List<AssetDesign> parseAndSaveAssets(String text, StepContext context) throws Exception {
        // 提取资产列表（剥离 Markdown 围栏，提示词不再要求 <<< >>> 包裹）
        String extracted = extractOutputContent(text);
        String[] lines = extracted.split("\n");

        // 1. 先解析 + 同批次按 (taskId, assetType, assetName) 去重（保留第一条）
        // 新格式6字段：资产类型|资产名称（含版本标识）|基础资产名|衍生自|资产描述|版本
        java.util.LinkedHashMap<String, AssetDesign> dedup = new java.util.LinkedHashMap<>();
        for (String line : lines) {
            if (line.trim().isEmpty() || line.startsWith("#") || line.contains("资产类型")) {
                continue;
            }
            String[] parts = line.split("\\|");
            if (parts.length >= 6) {
                AssetDesign asset = new AssetDesign();
                asset.setTaskId(context.getTaskId());
                asset.setAssetType(parts[0].trim());
                asset.setAssetName(parts[1].trim());
                asset.setBaseAssetName(parts[2].trim());
                asset.setDerivedFrom(parts[3].trim());
                asset.setAssetDesc(parts[4].trim());
                asset.setVersion(parseVersion(parts[5].trim()));
                String key = asset.getTaskId() + "|" + asset.getAssetType() + "|" + asset.getAssetName();
                if (!dedup.containsKey(key)) {
                    dedup.put(key, asset);
                }
            }
        }

        if (dedup.isEmpty()) {
            return assetDesignService.listByTaskId(context.getTaskId());
        }

        // 2. 逐条 upsert：存在则更新，不存在则插入
        int insertCnt = 0, updateCnt = 0;
        for (AssetDesign asset : dedup.values()) {
            AssetDesign existed = assetDesignService.lambdaQuery()
                    .eq(AssetDesign::getTaskId, asset.getTaskId())
                    .eq(AssetDesign::getAssetType, asset.getAssetType())
                    .eq(AssetDesign::getAssetName, asset.getAssetName())
                    .one();
            if (existed != null) {
                existed.setBaseAssetName(asset.getBaseAssetName());
                existed.setDerivedFrom(asset.getDerivedFrom());
                existed.setAssetDesc(asset.getAssetDesc());
                existed.setVersion(asset.getVersion());
                assetDesignService.updateById(existed);
                updateCnt++;
            } else {
                assetDesignService.save(asset);
                insertCnt++;
            }
        }
        log.info("[ASSET_DESIGN] 资产已保存(upsert)，insert={}, update={}, total={}",
                insertCnt, updateCnt, insertCnt + updateCnt);

        return assetDesignService.listByTaskId(context.getTaskId());
    }

    /** 解析版本号，默认1 */
    private static Integer parseVersion(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 1;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}
