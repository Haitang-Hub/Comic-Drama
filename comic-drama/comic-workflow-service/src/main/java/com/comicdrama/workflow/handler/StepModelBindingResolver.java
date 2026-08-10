package com.comicdrama.workflow.handler;

import com.comicdrama.workflow.entity.AiModelConfig;
import com.comicdrama.workflow.entity.StepModelBinding;
import com.comicdrama.workflow.mapper.AiModelConfigMapper;
import com.comicdrama.workflow.mapper.StepModelBindingMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 步骤-模型绑定解析器。
 * 从 step_model_binding 表加载配置，直接关联 ai_model_config 表获取完整的模型信息。
 * 支持管理员通过系统设置页面修改每个步骤使用的 AI 模型。
 */
@Slf4j
@Service
public class StepModelBindingResolver {

    private final StepModelBindingMapper bindingMapper;
    private final AiModelConfigMapper modelConfigMapper;

    /** 缓存：stepCode -> AiModelConfig（完整的模型配置） */
    private final Map<String, AiModelConfig> bindingCache = new HashMap<>();

    public StepModelBindingResolver(StepModelBindingMapper bindingMapper,
                                    AiModelConfigMapper modelConfigMapper) {
        this.bindingMapper = bindingMapper;
        this.modelConfigMapper = modelConfigMapper;
        reload();
    }

    /**
     * 重新加载所有步骤-模型绑定配置。
     * 从 step_model_binding 表读取绑定关系，关联 ai_model_config 表获取模型详情。
     */
    public void reload() {
        try {
            bindingCache.clear();
            List<StepModelBinding> bindings = bindingMapper.selectAllOrdered();
            if (bindings == null || bindings.isEmpty()) {
                log.warn("step_model_binding 表无启用的绑定配置");
                return;
            }

            for (StepModelBinding binding : bindings) {
                if (binding.getModelConfigId() == null) {
                    log.warn("绑定 {} 未设置 modelConfigId，跳过", binding.getStepCode());
                    continue;
                }
                AiModelConfig config = modelConfigMapper.selectById(binding.getModelConfigId());
                if (config != null && config.getStatus() != null && config.getStatus() == 1) {
                    bindingCache.put(binding.getStepCode(), config);
                    log.info("加载绑定: stepCode={} -> provider={}, model={}, type={}",
                            binding.getStepCode(), config.getModelProvider(),
                            config.getModelName(), config.getModelType());
                } else {
                    log.warn("绑定 {} 关联的模型配置 id={} 不存在或已禁用",
                            binding.getStepCode(), binding.getModelConfigId());
                }
            }
            log.info("加载步骤-模型绑定配置 {} 条", bindingCache.size());
        } catch (Exception e) {
            log.error("加载步骤-模型绑定配置失败", e);
        }
    }

    /**
     * 根据步骤枚举获取配置的模型服务商。
     * 如果步骤不需要模型，或没有绑定配置，返回步骤默认的模型服务商。
     */
    public String resolveModelProvider(StepEnum step, String defaultModelProvider) {
        AiModelConfig config = resolveModelConfig(step);
        if (config != null) {
            return config.getModelProvider();
        }
        return defaultModelProvider;
    }

    /**
     * 根据步骤枚举获取配置的完整模型配置。
     * 优先使用绑定配置，没有绑定则使用步骤枚举的默认值。
     *
     * @param step 步骤枚举
     * @return AiModelConfig（可能为 null，表示使用默认）
     */
    public AiModelConfig resolveModelConfig(StepEnum step) {
        if (step == null || !step.isModelRequired()) {
            return null;
        }
        return bindingCache.get(step.name());
    }

    /**
     * 验证所有必填步骤的模型配置是否存在且已启用。
     * 跳过 AUDIO 步骤（配音可跳过）。
     *
     * @param skipAudio 是否跳过 AUDIO 步骤的验证（voiceEnabled=0 时为 true）
     * @return 缺失配置的步骤列表
     */
    public List<StepEnum> validateRequiredBindings(boolean skipAudio) {
        List<StepEnum> missing = new java.util.ArrayList<>();
        for (StepEnum step : StepEnum.values()) {
            if (!step.isModelRequired()) {
                continue;
            }
            if (skipAudio && step == StepEnum.AUDIO) {
                continue;
            }
            AiModelConfig config = bindingCache.get(step.name());
            if (config == null) {
                missing.add(step);
                log.warn("步骤 {} 缺少模型配置", step.getName());
            }
        }
        return missing;
    }
    public void refreshStep(StepEnum step) {
        try {
            StepModelBinding binding = bindingMapper.selectByStepCode(step.name());
            if (binding != null && binding.getModelConfigId() != null) {
                AiModelConfig config = modelConfigMapper.selectById(binding.getModelConfigId());
                if (config != null && config.getStatus() != null && config.getStatus() == 1) {
                    bindingCache.put(step.name(), config);
                    log.info("刷新步骤绑定: stepCode={} -> provider={}, model={}",
                            step.name(), config.getModelProvider(), config.getModelName());
                } else {
                    bindingCache.remove(step.name());
                }
            } else {
                bindingCache.remove(step.name());
            }
        } catch (Exception e) {
            log.error("刷新步骤绑定失败: stepCode={}", step.name(), e);
        }
    }
}
