package com.comicdrama.task.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.comicdrama.common.entity.BaseTimeEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 任务节点状态表（全节点精细化管控：单点查看/编辑/重生成）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("task_node_state")
public class TaskNodeState extends BaseTimeEntity {

    private Long taskId;

    private Integer step;

    private String nodeType;

    private String nodeKey;

    private String nodeName;

    /** 节点状态：0等待 1进行中 2成功 3失败 */
    private Integer nodeStatus;

    private Integer canRegenerate;

    private Integer regenerateCount;

    private LocalDateTime lastRegenerateTime;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Long durationMs;

    /** 输入快照(JSON) */
    private String inputSnapshot;

    /** 输出快照(JSON) */
    private String outputSnapshot;

    private String errorMsg;

    private Integer retryCount;
}
