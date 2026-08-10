package com.comicdrama.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.comicdrama.common.entity.BaseTimeEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 分镜脚本表（步骤2产物：分镜生成）
 * 格式：分镜序号|本镜时长|场景分组ID|组内序号|镜头角度|镜头描述|出场角色|分镜描述|台词内容|画面描述
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("storyboard")
public class Storyboard extends BaseTimeEntity {

    private Long taskId;

    /** 分镜序号（全局递增，从1开始） */
    private Integer seq;

    /** 本镜时长（秒） */
    private Integer duration;

    /** 场景分组ID（从1开始） */
    private Integer groupId;

    /** 组内序号（同场景组内序号，从1开始） */
    private Integer localSeq;

    /** 镜头角度（近景/远景/俯视等） */
    private String cameraAngle;

    /** 镜头描述（动作、运镜） */
    private String shotDesc;

    /** 场景（场景名称_版本标识，分号分隔） */
    private String scene;

    /** 出场角色（角色名称_版本标识，分号分隔，无则写"无"） */
    @TableField("`character`")
    private String character;

    /** 出场道具（道具名称_版本标识，分号分隔，无则写"无"） */
    private String props;

    /** 分镜描述 */
    private String storyboardDesc;

    /** 台词内容（逗号分隔，无则写"无"） */
    private String dialogue;

    /** 画面描述（不包含画风） */
    private String visualDesc;

    /** 是否被用户手动编辑：0否 1是 */
    private Integer isEdited;
}
