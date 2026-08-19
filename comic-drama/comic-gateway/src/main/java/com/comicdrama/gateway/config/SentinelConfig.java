package com.comicdrama.gateway.config;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Sentinel 规则配置（API 网关层）。
 * <p>
 * 1. AI 调用接口配置降级规则（熔断/限流）
 * 2. 任务提交接口配置 QPS 限制
 * 3. 全局限流保护
 */
@Slf4j
@Configuration
public class SentinelConfig {

    @Value("${sentinel.flow.default-qps:200}")
    private int defaultQps;

    @Value("${sentinel.flow.task-submit-qps:10}")
    private int taskSubmitQps;

    @Value("${sentinel.flow.ai-call-qps:5}")
    private int aiCallQps;

    @Value("${sentinel.degrade.ai-call-rt-ms:30000}")
    private long aiCallRtMs;

    @Value("${sentinel.degrade.ai-call-error-ratio:0.5}")
    private double aiCallErrorRatio;

    @PostConstruct
    public void init() {
        List<FlowRule> flowRules = new ArrayList<>();
        List<DegradeRule> degradeRules = new ArrayList<>();

        flowRules.add(buildFlowRule("comic-workflow-service", defaultQps));
        flowRules.add(buildFlowRule("comic-task-service", defaultQps));
        flowRules.add(buildFlowRule("comic-resource-service", defaultQps));

        flowRules.add(buildFlowRule("task-submit", taskSubmitQps));

        flowRules.add(buildFlowRule("ai-call", aiCallQps));

        FlowRuleManager.loadRules(flowRules);
        log.info("[Sentinel] 限流规则加载完成，共 {} 条", flowRules.size());

        degradeRules.add(buildRtDegradeRule("ai-call", aiCallRtMs));
        degradeRules.add(buildErrorRatioDegradeRule("ai-call", aiCallErrorRatio));
        degradeRules.add(buildRtDegradeRule("comic-workflow-service", 60000));
        degradeRules.add(buildErrorRatioDegradeRule("comic-workflow-service", 0.6));

        DegradeRuleManager.loadRules(degradeRules);
        log.info("[Sentinel] 降级规则加载完成，共 {} 条", degradeRules.size());
    }

    private FlowRule buildFlowRule(String resource, int qps) {
        FlowRule rule = new FlowRule();
        rule.setResource(resource);
        rule.setGrade(1); // QPS
        rule.setCount(qps);
        rule.setStrategy(0); // DIRECT
        rule.setControlBehavior(0); // DIRECT
        return rule;
    }

    private DegradeRule buildRtDegradeRule(String resource, long rtMs) {
        DegradeRule rule = new DegradeRule();
        rule.setResource(resource);
        rule.setGrade(0); // RT
        rule.setCount(rtMs);
        rule.setTimeWindow(10);
        return rule;
    }

    private DegradeRule buildErrorRatioDegradeRule(String resource, double ratio) {
        DegradeRule rule = new DegradeRule();
        rule.setResource(resource);
        rule.setGrade(1); // ERROR_RATIO
        rule.setCount(ratio);
        rule.setTimeWindow(10);
        rule.setMinRequestAmount(5);
        return rule;
    }

    @SentinelResource(value = "gateway-default",
            blockHandler = "gatewayBlockHandler",
            fallback = "gatewayFallback")
    public String gatewayDefault(String msg) {
        return msg;
    }

    public String gatewayBlockHandler(String msg, BlockException ex) {
        log.warn("[Sentinel] 网关默认降级触发: {}", ex.getRule());
        return "{\"code\":1001,\"msg\":\"服务暂不可用（限流）\",\"data\":null,\"timestamp\":" + System.currentTimeMillis() + "}";
    }

    public String gatewayFallback(String msg, Throwable t) {
        log.warn("[Sentinel] 网关默认熔断触发: {}", t.getMessage());
        return "{\"code\":1001,\"msg\":\"服务暂不可用（熔断）\",\"data\":null,\"timestamp\":" + System.currentTimeMillis() + "}";
    }
}
