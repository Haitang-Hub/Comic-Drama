package com.comicdrama.task.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.comicdrama.common.entity.BaseCreateTimeEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 任务进度日志表（实时WebSocket推送进度依据）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("task_progress_log")
public class TaskProgressLog extends BaseCreateTimeEntity {

    private Long taskId;

    /** 所属步骤（1-8，0表示任务级事件） */
    private Integer step;

    private String nodeType;

    private String nodeKey;

    private Integer progress;

    private Integer totalProgress;

    private String message;

    /** 是否已WebSocket推送：0否 1是 */
    private Integer isPushed;
}
