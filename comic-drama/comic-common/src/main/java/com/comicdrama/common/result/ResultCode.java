package com.comicdrama.common.result;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 统一响应状态码
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    SUCCESS(200, "操作成功"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无操作权限"),
    NOT_FOUND(404, "资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方式不支持"),
    INTERNAL_ERROR(500, "系统内部错误"),

    /** 业务错误码段 1000+ */
    BIZ_ERROR(1000, "业务处理失败"),
    SERVICE_UNAVAILABLE(1001, "服务暂不可用"),
    PARAM_VALIDATE_FAILED(1002, "参数校验失败"),
    DATA_NOT_FOUND(1003, "数据不存在"),
    DATA_DUPLICATED(1004, "数据已存在"),
    STATUS_ILLEGAL(1005, "状态非法，不允许此操作"),

    /** 用户/权限 1100+ */
    USER_NOT_FOUND(1100, "用户不存在"),
    USERNAME_OR_PASSWORD_ERROR(1101, "用户名或密码错误"),
    ACCOUNT_DISABLED(1102, "账号已禁用"),
    USERNAME_EXISTS(1103, "用户名已存在"),

    /** 任务 1200+ */
    TASK_NOT_FOUND(1200, "任务不存在"),
    TASK_STATUS_ILLEGAL(1201, "任务状态不允许此操作"),
    QUEUE_FULL(1202, "任务队列已满"),

    /** 存储/模型 1300+ */
    STORAGE_ERROR(1300, "存储服务异常"),
    MODEL_UNAVAILABLE(1301, "AI 模型不可用"),
    QUOTA_INSUFFICIENT(1302, "配额不足");

    private final int code;
    private final String msg;
}
