package com.comicdrama.common.broadcast.event;

import com.comicdrama.common.constant.CacheConstants;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.io.Serial;

/**
 * 任务进度事件（WebSocket 推送依据）。
 * <p>
 * 字段规范：
 * - step/totalProgress/status：主进度条展示使用
 * - stepName/itemDone/itemTotal：批量步骤子进度条（"2/5 已完成"）展示使用
 * - message：精炼一句话（建议4~12字，如"生成中""已完成"）
 */
@Getter
public class TaskProgressEvent extends ApplicationEvent {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Long taskId;
    private final Integer step;
    private final String stepName;
    private final String nodeKey;
    private final Integer progress;
    private final Integer totalProgress;
    private final Integer itemDone;
    private final Integer itemTotal;
    private final Integer status;
    private final String message;

    /**
     * @deprecated 请使用带 stepName / itemDone / itemTotal / status 的新构造器
     */
    @Deprecated
    public TaskProgressEvent(Object source, Long taskId, Integer step, String nodeKey,
                             Integer progress, Integer totalProgress, String message) {
        this(source, taskId, step, null, nodeKey, progress, totalProgress, null, null, null, message);
    }

    public TaskProgressEvent(Object source, Long taskId, Integer step, String stepName, String nodeKey,
                             Integer progress, Integer totalProgress,
                             Integer itemDone, Integer itemTotal, Integer status, String message) {
        super(source);
        this.taskId = taskId;
        this.step = step;
        this.stepName = stepName;
        this.nodeKey = nodeKey;
        this.progress = progress;
        this.totalProgress = totalProgress;
        this.itemDone = itemDone;
        this.itemTotal = itemTotal;
        this.status = status;
        this.message = message;
    }

    public String channel() {
        return CacheConstants.CHANNEL_TASK_PROGRESS;
    }
}
