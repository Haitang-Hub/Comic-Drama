package com.comicdrama.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.comicdrama.common.dto.PageQuery;
import com.comicdrama.common.result.PageResult;
import com.comicdrama.workflow.entity.TokenUsageLog;
import com.comicdrama.workflow.mapper.TokenUsageLogMapper;
import com.comicdrama.workflow.service.TokenUsageLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TokenUsageLogServiceImpl extends ServiceImpl<TokenUsageLogMapper, TokenUsageLog> implements TokenUsageLogService {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public PageResult<TokenUsageLog> page(PageQuery query, String keyword, String modelName, Integer modelType) {
        LambdaQueryWrapper<TokenUsageLog> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(TokenUsageLog::getModelName, keyword));
        }
        if (StringUtils.hasText(modelName)) {
            wrapper.eq(TokenUsageLog::getModelName, modelName);
        }
        if (modelType != null) {
            wrapper.eq(TokenUsageLog::getModelType, modelType);
        }
        wrapper.orderByDesc(TokenUsageLog::getCreateTime);
        Page<TokenUsageLog> page = new Page<>(query.getPage(), query.getSize());
        Page<TokenUsageLog> result = this.page(page, wrapper);
        return new PageResult<>(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public PageResult<TokenUsageLog> pageAdmin(PageQuery query, Long taskId, String modelProvider,
                                                String modelName, LocalDate startDate, LocalDate endDate) {
        LambdaQueryWrapper<TokenUsageLog> wrapper = new LambdaQueryWrapper<>();
        if (taskId != null) {
            wrapper.eq(TokenUsageLog::getTaskId, taskId);
        }
        if (StringUtils.hasText(modelProvider)) {
            wrapper.and(w -> w.like(TokenUsageLog::getModelName, modelProvider));
        }
        if (StringUtils.hasText(modelName)) {
            wrapper.eq(TokenUsageLog::getModelName, modelName);
        }
        if (startDate != null) {
            wrapper.ge(TokenUsageLog::getCreateTime, startDate.atStartOfDay());
        }
        if (endDate != null) {
            wrapper.le(TokenUsageLog::getCreateTime, endDate.atTime(LocalTime.MAX));
        }
        wrapper.orderByDesc(TokenUsageLog::getCreateTime);
        Page<TokenUsageLog> page = new Page<>(query.getPage(), query.getSize());
        Page<TokenUsageLog> result = this.page(page, wrapper);
        return new PageResult<>(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public Map<String, Object> aggregate(LocalDate startDate, LocalDate endDate) {
        Map<String, Object> result = new HashMap<>();
        LocalDateTime start = startDate != null ? startDate.atStartOfDay() : null;
        LocalDateTime end = endDate != null ? endDate.atTime(LocalTime.MAX) : null;

        StringBuilder totalsSql = new StringBuilder(
                "SELECT COALESCE(SUM(prompt_tokens), 0) AS totalInputTokens, " +
                        "COALESCE(SUM(completion_tokens), 0) AS totalOutputTokens, " +
                        "COALESCE(SUM(total_tokens), 0) AS totalTokens " +
                        "FROM token_usage_log WHERE 1=1");
        if (start != null) totalsSql.append(" AND create_time >= '").append(start).append("'");
        if (end != null) totalsSql.append(" AND create_time <= '").append(end).append("'");

        Map<String, Object> totals = jdbcTemplate.queryForMap(totalsSql.toString());
        result.put("totalInputTokens", totals.get("totalInputTokens"));
        result.put("totalOutputTokens", totals.get("totalOutputTokens"));
        result.put("totalTokens", totals.get("totalTokens"));

        StringBuilder groupSql = new StringBuilder(
                "SELECT model_name AS modelName, " +
                        "COALESCE(SUM(prompt_tokens), 0) AS inputTokens, " +
                        "COALESCE(SUM(completion_tokens), 0) AS outputTokens, " +
                        "COALESCE(SUM(total_tokens), 0) AS totalTokens, " +
                        "COUNT(*) AS callCount " +
                        "FROM token_usage_log WHERE 1=1");
        if (start != null) groupSql.append(" AND create_time >= '").append(start).append("'");
        if (end != null) groupSql.append(" AND create_time <= '").append(end).append("'");
        groupSql.append(" GROUP BY model_name ORDER BY totalTokens DESC");

        List<Map<String, Object>> groupList = jdbcTemplate.queryForList(groupSql.toString());
        result.put("groupByModel", groupList);

        return result;
    }
}