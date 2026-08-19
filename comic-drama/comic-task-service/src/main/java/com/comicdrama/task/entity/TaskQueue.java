package com.comicdrama.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 任务队列表（排队调度、并发管控）。
 * 该表无 create_time/update_time/deleted，仅有自己的时间字段，故不继承审计基类。
 */
@Data
@TableName("task_queue")
public class TaskQueue implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long taskId;

    private Long userId;

    /** 队列状态：0等待中 1执行中 2已完成 3已取消 */
    private Integer queueStatus;

    /** 优先级（数字越小优先级越高） */
    private Integer priority;

    private Integer queuePosition;

    private LocalDateTime enqueuedTime;

    private LocalDateTime startedTime;

    private LocalDateTime finishedTime;
}
