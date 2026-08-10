package com.comicdrama.common.exception;

import com.comicdrama.common.result.ResultCode;
import lombok.Getter;

import java.io.Serial;

/**
 * 业务异常
 */
@Getter
public class BizException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final int code;

    private final Object data;

    public BizException(String msg) {
        super(msg);
        this.code = ResultCode.BIZ_ERROR.getCode();
        this.data = null;
    }

    public BizException(int code, String msg) {
        super(msg);
        this.code = code;
        this.data = null;
    }

    public BizException(ResultCode resultCode) {
        super(resultCode.getMsg());
        this.code = resultCode.getCode();
        this.data = null;
    }

    public BizException(ResultCode resultCode, String msg) {
        super(msg);
        this.code = resultCode.getCode();
        this.data = null;
    }

    public BizException(ResultCode resultCode, Object data) {
        super(resultCode.getMsg());
        this.code = resultCode.getCode();
        this.data = data;
    }
}
