package com.comicdrama.system.aspect;

import com.comicdrama.common.util.SecurityUtils;
import com.comicdrama.system.entity.OperationLog;
import com.comicdrama.system.service.OperationLogAsyncService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

/**
 * 操作日志切面。
 * 拦截带 @OperationLogMark 注解的方法，记录操作人、模块、动作、请求参数摘要、耗时、IP、结果，
 * 异步写入 operation_log 表。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    private final OperationLogAsyncService asyncService;
    private final ObjectMapper objectMapper;

    @Around("@annotation(com.comicdrama.system.aspect.OperationLogMark)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        OperationLogMark annotation = method.getAnnotation(OperationLogMark.class);

        OperationLog operationLog = new OperationLog();
        operationLog.setModule(annotation.module());
        operationLog.setBusinessType(annotation.action());
        operationLog.setMethod(joinPoint.getTarget().getClass().getName() + "." + method.getName());

        fillRequestInfo(operationLog);
        fillUserInfo(operationLog);

        Object result = null;
        Throwable exception = null;
        try {
            result = joinPoint.proceed();
            operationLog.setStatus(1);
        } catch (Throwable ex) {
            exception = ex;
            operationLog.setStatus(0);
            operationLog.setErrorMsg(truncate(ex.getMessage(), 500));
            throw ex;
        } finally {
            int costTime = (int) (System.currentTimeMillis() - startTime);
            operationLog.setCostTime(costTime);
            operationLog.setRequestParam(truncate(toJson(joinPoint.getArgs()), 1000));
            operationLog.setResponseData(truncate(toJson(result), 1000));

            asyncService.saveLog(operationLog);
        }
        return result;
    }

    private void fillRequestInfo(OperationLog operationLog) {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                operationLog.setRequestUrl(request.getRequestURI());
                operationLog.setRequestMethod(request.getMethod());
                operationLog.setIp(getClientIp(request));
            }
        } catch (Exception e) {
            log.warn("Failed to fill request info: {}", e.getMessage());
        }
    }

    private void fillUserInfo(OperationLog operationLog) {
        try {
            Long userId = SecurityUtils.getCurrentUserIdOrNull();
            operationLog.setUserId(userId);
            String username = SecurityUtils.getCurrentUsername();
            operationLog.setUsername(username);
        } catch (Exception e) {
            log.warn("Failed to fill user info: {}", e.getMessage());
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    private String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return String.valueOf(obj);
        }
    }

    private String truncate(String str, int maxLen) {
        if (str == null) {
            return null;
        }
        return str.length() > maxLen ? str.substring(0, maxLen) + "..." : str;
    }
}
