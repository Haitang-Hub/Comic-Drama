package com.comicdrama.workflow.controller;

import com.comicdrama.common.result.Result;
import com.comicdrama.workflow.entity.StepModelBinding;
import com.comicdrama.workflow.handler.StepEnum;
import com.comicdrama.workflow.handler.StepModelBindingResolver;
import com.comicdrama.workflow.mapper.StepModelBindingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理后台聚合 API（前端统一走 /api/admin/** 路径，路由到 workflow-service）。
 * 本类负责：首页统计卡片、系统设置 Tab 的启用模型列表、步骤绑定列表。
 */
@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminApiController {

    private final JdbcTemplate jdbcTemplate;
    private final StepModelBindingMapper stepModelBindingMapper;
    private final StepModelBindingResolver bindingResolver;

    // -------------------------------------------------------------------------
    // 1. 首页统计卡片（对应前端 SystemStatsVO：
    //    userTotal, userActive, taskTotal, taskRunning, taskDone, taskFailed,
    //    workTotal, todayNewUsers, todayNewTasks）
    // -------------------------------------------------------------------------
    @GetMapping("/stats")
    public Result<Map<String, Object>> getSystemStats() {
        Map<String, Object> stats = new HashMap<>();

        stats.put("userTotal", count("SELECT COUNT(*) FROM sys_user WHERE deleted = 0"));
        stats.put("userActive", count(
                "SELECT COUNT(DISTINCT user_id) FROM comic_task WHERE deleted = 0 AND create_time >= CURDATE()"));
        stats.put("taskTotal", count("SELECT COUNT(*) FROM comic_task WHERE deleted = 0"));
        stats.put("taskRunning",
                count("SELECT COUNT(*) FROM comic_task WHERE deleted = 0 AND status IN (0, 1, 4)"));
        stats.put("taskDone", count("SELECT COUNT(*) FROM comic_task WHERE deleted = 0 AND status = 2"));
        stats.put("taskFailed", count("SELECT COUNT(*) FROM comic_task WHERE deleted = 0 AND status = 3"));
        stats.put("workTotal", count("SELECT COUNT(*) FROM comic_work WHERE deleted = 0"));
        stats.put("todayNewUsers",
                count("SELECT COUNT(*) FROM sys_user WHERE deleted = 0 AND DATE(create_time) = CURDATE()"));
        stats.put("todayNewTasks",
                count("SELECT COUNT(*) FROM comic_task WHERE deleted = 0 AND DATE(create_time) = CURDATE()"));

        return Result.ok(stats);
    }

    // -------------------------------------------------------------------------
    // 2. 启用的 AI 模型列表（系统设置 tab 的模型下拉框使用）
    //    返回字段：id, model_provider, model_name, model_type, protocol, capabilities,
    //             selector_strategy, weight, status, api_url, create_time, update_time
    // -------------------------------------------------------------------------
    @GetMapping("/model/active-list")
    public Result<List<Map<String, Object>>> listActiveModels() {
        String sql =
                "SELECT id, model_provider AS modelProvider, model_name AS modelName, " +
                        "model_type AS modelType, protocol, capabilities, " +
                        "selector_strategy AS selectorStrategy, weight, status, api_url AS apiUrl, " +
                        "api_key AS apiKey, create_time AS createTime, update_time AS updateTime, deleted " +
                        "FROM ai_model_config WHERE status = 1 AND deleted = 0 ORDER BY model_type, id";
        return Result.ok(jdbcTemplate.queryForList(sql));
    }

    // -------------------------------------------------------------------------
    // 3. 步骤-模型绑定列表（与 /api/admin/step-binding/list 功能一致，用于系统设置 tab）
    // -------------------------------------------------------------------------
    @GetMapping("/binding/list")
    public Result<List<StepModelBinding>> listBindings() {
        return Result.ok(stepModelBindingMapper.selectAllOrdered());
    }

    // -------------------------------------------------------------------------
    // 4. 创建步骤-模型绑定（POST /api/admin/binding）
    // -------------------------------------------------------------------------
    @PostMapping("/binding")
    public Result<StepModelBinding> createBinding(@RequestBody StepModelBinding binding) {
        binding.setId(null);
        stepModelBindingMapper.insert(binding);
        refreshBindingCache(binding);
        return Result.ok(binding);
    }

    // -------------------------------------------------------------------------
    // 5. 更新步骤-模型绑定（PUT /api/admin/binding/{id}）
    //    ⚠️ 更新数据库后必须刷新 StepModelBindingResolver 内存缓存，
    //    否则任务执行时仍读取启动时加载的旧绑定（如已从 deepseek 改为 mock，但实际仍用 deepseek）
    // -------------------------------------------------------------------------
    @PutMapping("/binding/{id}")
    public Result<Void> updateBinding(@PathVariable Long id, @RequestBody StepModelBinding binding) {
        binding.setId(id);
        stepModelBindingMapper.updateById(binding);
        refreshBindingCache(binding);
        return Result.ok();
    }

    // -------------------------------------------------------------------------
    // 6. 清除步骤-模型绑定（PUT /api/admin/binding/{id}/clear，将 modelConfigId 设为 null）
    // -------------------------------------------------------------------------
    @PutMapping("/binding/{id}/clear")
    public Result<Void> clearBinding(@PathVariable Long id) {
        StepModelBinding binding = stepModelBindingMapper.selectById(id);
        if (binding != null) {
            binding.setModelConfigId(null);
            stepModelBindingMapper.updateById(binding);
            refreshBindingCache(binding);
        }
        return Result.ok();
    }

    /**
     * 刷新步骤-模型绑定内存缓存。
     * StepModelBindingResolver 在服务启动时把 step_model_binding 全量加载到内存 Map，
     * 之后 resolveModelConfig(step) 只读缓存不查库。
     * 任何写操作（新增/更新/清除）后必须调用 refreshStep，否则任务执行用的是旧缓存。
     */
    private void refreshBindingCache(StepModelBinding binding) {
        if (binding == null || binding.getStepCode() == null) {
            return;
        }
        try {
            bindingResolver.refreshStep(StepEnum.valueOf(binding.getStepCode()));
        } catch (IllegalArgumentException e) {
            log.warn("刷新绑定缓存失败：未知 stepCode={}", binding.getStepCode());
        }
    }

    // =========================================================================
    // 私有工具：返回 count SQL 的 int 结果，null 兜底为 0
    // =========================================================================
    private int count(String sql) {
        Long v = jdbcTemplate.queryForObject(sql, Long.class);
        return v != null ? v.intValue() : 0;
    }
}
