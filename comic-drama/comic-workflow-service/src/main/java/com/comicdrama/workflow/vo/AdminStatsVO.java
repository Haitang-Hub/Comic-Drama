package com.comicdrama.workflow.vo;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class AdminStatsVO {

    private long modelCount;

    private long templateCount;

    private long pricingCount;

    private long totalTokensToday;

    private double totalCostToday;

    private Map<String, Long> tokenByModel = new HashMap<>();
}