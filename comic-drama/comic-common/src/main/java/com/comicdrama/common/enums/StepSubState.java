package com.comicdrama.common.enums;

import lombok.Getter;

/**
 * 步骤子状态枚举。
 * 用于追踪步骤在测试优先+批量执行模式下的细粒度状态。
 *
 * <p>状态流转：</p>
 * <pre>
 *   IDLE → TESTING → TEST_SUCCESS → BATCHING → COMPLETE
 *                  ↘ TEST_FAILED
 *        任何状态 → PAUSED → (恢复后从当前子状态继续)
 * </pre>
 */
@Getter
public enum StepSubState {

    /** 等待执行 */
    IDLE(0, "等待"),

    /** 进行中（正在生成第一个产物） */
    TESTING(1, "进行中"),

    /** 测试成功，等待用户确认 */
    TEST_SUCCESS(2, "测试成功"),

    /** 测试失败 */
    TEST_FAILED(3, "测试失败"),

    /** 批量生成中（正在批量生成剩余产物） */
    BATCHING(4, "批量中"),

    /** 步骤已暂停 */
    PAUSED(5, "已暂停"),

    /** 步骤完成 */
    COMPLETE(6, "已完成"),

    /** 步骤失败（批量过程中失败） */
    FAILED(7, "失败");

    private final int code;
    private final String label;

    StepSubState(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public static StepSubState of(Integer code) {
        if (code == null) {
            return null;
        }
        for (StepSubState s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        return null;
    }
}
