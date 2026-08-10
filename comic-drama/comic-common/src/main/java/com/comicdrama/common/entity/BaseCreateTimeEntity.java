package com.comicdrama.common.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 仅创建时间审计基础实体（无更新时间、无逻辑删除）。
 * 适用于日志/版本等只追加不修改的表：
 *   prompt_template_version / operation_log / task_progress_log /
 *   task_failure_log / resource_cleanup_log / comic_work_timeline
 */
@Data
public abstract class BaseCreateTimeEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
