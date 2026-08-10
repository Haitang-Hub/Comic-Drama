package com.comicdrama.common.broadcast.event;

import com.comicdrama.common.constant.CacheConstants;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.io.Serial;

/** 任务状态变更事件 */
@Getter
public class TaskStatusChangeEvent extends ApplicationEvent {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Long taskId;
    private final Integer oldStatus;
    private final Integer newStatus;

    public TaskStatusChangeEvent(Object source, Long taskId, Integer oldStatus, Integer newStatus) {
        super(source);
        this.taskId = taskId;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
    }

    public String channel() {
        return CacheConstants.CHANNEL_TASK_STATUS;
    }
}
