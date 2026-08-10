package com.comicdrama.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.comicdrama.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 漫剧任务轻量实体（仅用于统计查询）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("comic_task")
public class ComicTaskLite extends BaseEntity {

    /** 任务状态：0排队 1生成中 2已完成 3失败 4已暂停 */
    private Integer status;

    private Long userId;
}
