package com.comicdrama.workflow.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.comicdrama.common.dto.PageQuery;
import com.comicdrama.common.result.PageResult;
import com.comicdrama.workflow.entity.TokenUsageLog;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface TokenUsageLogService extends IService<TokenUsageLog> {
    PageResult<TokenUsageLog> page(PageQuery query, String keyword, String modelName, Integer modelType);

    PageResult<TokenUsageLog> pageAdmin(PageQuery query, Long taskId, String modelProvider,
                                         String modelName, LocalDate startDate, LocalDate endDate);

    Map<String, Object> aggregate(LocalDate startDate, LocalDate endDate);
}