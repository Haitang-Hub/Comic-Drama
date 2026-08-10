package com.comicdrama.resource.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.comicdrama.common.entity.BaseCreateTimeEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 资源清理日志表（生命周期管理审计） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("resource_cleanup_log")
public class ResourceCleanupLog extends BaseCreateTimeEntity {

    private Long resourceId;
    private Long taskId;
    private String fileName;
    private String objectKey;
    private Long fileSize;
    private String cleanupType;
    private String cleanupReason;
    private String operator;
}
