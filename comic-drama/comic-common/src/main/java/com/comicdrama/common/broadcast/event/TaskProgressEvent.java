package com.comicdrama.common.broadcast.event;

import com.comicdrama.common.constant.CacheConstants;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.io.Serial;

/**
 * 任务进度事件（WebSocket 推送依据）。
 */
@Getter
public class TaskProgressEvent extends ApplicationEvent {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Long taskId;
    private final Integer step;
    private final String nodeKey;
    private final Integer progress;
    private final Integer totalProgress;
    private final String message;

    public TaskProgressEvent(Object source, Long taskId, Integer step, String nodeKey,
                             Integer progress, Integer totalProgress, String message) {
        super(source);
        this.taskId = taskId;
        this.step = step;
        this.nodeKey = nodeKey;
        this.progress = progress;
        this.totalProgress = totalProgress;
        this.message = message;
    }

    public String channel() {
        return CacheConstants.CHANNEL_TASK_PROGRESS;
    }
}
