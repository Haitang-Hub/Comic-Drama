package com.comicdrama.common.queue;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 任务队列条目
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskQueueEntry implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long taskId;
    private Long userId;
    private Integer priority;
    private LocalDateTime enqueuedTime;
    private Integer queuePosition;
}
