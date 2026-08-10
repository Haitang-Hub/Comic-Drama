package com.comicdrama.common.exception;

import com.comicdrama.common.result.ResultCode;
import lombok.Getter;

import java.io.Serial;

/**
 * 任务暂停异常：当步骤处理器检测到任务已被暂停时抛出。
 * 工作流引擎捕获此异常后应保存当前进度并安全退出。
 */
@Getter
public class TaskPausedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final int stepOrder;

    public TaskPausedException(int stepOrder, String message) {
        super(message);
        this.stepOrder = stepOrder;
    }

    public TaskPausedException(int stepOrder) {
        super("任务在步骤 " + stepOrder + " 被暂停");
        this.stepOrder = stepOrder;
    }
}
