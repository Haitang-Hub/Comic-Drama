package com.comicdrama.common.result;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 统一响应结构
 */
@Data
public class Result<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 业务状态码：200 成功，其余为失败 */
    private int code;

    /** 提示信息 */
    private String msg;

    /** 业务数据 */
    private T data;

    /** 时间戳 */
    private long timestamp;

    public Result() {
        this.timestamp = System.currentTimeMillis();
    }

    public Result(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
    }

    public static <T> Result<T> ok() {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMsg(), null);
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMsg(), data);
    }

    public static <T> Result<T> ok(String msg, T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), msg, data);
    }

    public static <T> Result<T> fail(String msg) {
        return new Result<>(ResultCode.BIZ_ERROR.getCode(), msg, null);
    }

    public static <T> Result<T> fail(int code, String msg) {
        return new Result<>(code, msg, null);
    }

    public static <T> Result<T> fail(ResultCode resultCode) {
        return new Result<>(resultCode.getCode(), resultCode.getMsg(), null);
    }

    public static <T> Result<T> fail(ResultCode resultCode, String msg) {
        return new Result<>(resultCode.getCode(), msg, null);
    }

    public boolean success() {
        return this.code == ResultCode.SUCCESS.getCode();
    }
}
