package com.comicdrama.system.aspect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志自定义注解。
 * 标记需要记录操作日志的方法，由 OperationLogAspect 切面拦截处理。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationLogMark {

    /** 功能模块（如：prompt/system/ai_model） */
    String module() default "";

    /** 业务动作（如：create/update/delete/rollback） */
    String action() default "";
}
