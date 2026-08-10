package com.comicdrama.system.controller;

import com.comicdrama.common.dto.PageQuery;
import com.comicdrama.common.enums.ModelCapability;
import com.comicdrama.common.enums.ModelProtocol;
import com.comicdrama.common.enums.SelectorStrategy;
import com.comicdrama.common.result.PageResult;
import com.comicdrama.common.result.Result;
import com.comicdrama.system.dto.AiModelStatusDTO;
import com.comicdrama.system.dto.AiModelTestResultDTO;
import com.comicdrama.system.entity.AiModelConfig;
import com.comicdrama.system.service.AiModelConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI模型配置管理（管理员）
 */
@RestController
@RequestMapping("/api/system/model")
@RequiredArgsConstructor
public class AiModelConfigController {

    private final AiModelConfigService aiModelConfigService;

    @GetMapping("/page")
    public Result<PageResult<AiModelConfig>> page(PageQuery query,
                                                  @RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) Integer modelType) {
        return Result.ok(aiModelConfigService.page(query, keyword, modelType));
    }

    @GetMapping("/list")
    public Result<List<AiModelConfig>> list() {
        return Result.ok(aiModelConfigService.listEnabled());
    }

    @GetMapping("/{id}")
    public Result<AiModelConfig> get(@PathVariable Long id) {
        return Result.ok(aiModelConfigService.getMaskedById(id));
    }

    @PostMapping
    public Result<Void> add(@RequestBody AiModelConfig config) {
        aiModelConfigService.save(config);
        return Result.ok();
    }

    @PutMapping
    public Result<Void> update(@RequestBody AiModelConfig config) {
        aiModelConfigService.updateById(config);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        aiModelConfigService.removeById(id);
        return Result.ok();
    }

    /** 启用/禁用 */
    @PutMapping("/{id}/status")
    public Result<Void> toggleStatus(@PathVariable Long id, @RequestBody AiModelStatusDTO dto) {
        aiModelConfigService.toggleStatus(id, dto.getStatus());
        return Result.ok();
    }

    // ==================== 协议化改造新增接口 ====================

    /**
     * 返回所有可选调用协议（供前端下拉）。
     */
    @GetMapping("/protocols")
    public Result<List<ModelProtocol>> protocols() {
        return Result.ok(ModelProtocol.allProtocols());
    }

    /**
     * 返回所有可选能力声明（供前端多选）。
     */
    @GetMapping("/capabilities")
    public Result<List<ModelCapability>> capabilities() {
        return Result.ok(List.of(ModelCapability.values()));
    }

    /**
     * 返回所有可选负载均衡策略（供前端下拉）。
     */
    @GetMapping("/selector-strategies")
    public Result<List<SelectorStrategy>> selectorStrategies() {
        return Result.ok(SelectorStrategy.allStrategies());
    }

    /**
     * 连通性测试：按模型配置的协议发起最小化探测请求。
     */
    @PostMapping("/{id}/test")
    public Result<AiModelTestResultDTO> test(@PathVariable Long id) {
        return Result.ok(aiModelConfigService.testConnection(id));
    }
}
