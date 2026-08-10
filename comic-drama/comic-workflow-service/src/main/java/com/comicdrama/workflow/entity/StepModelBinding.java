package com.comicdrama.workflow.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 步骤-模型绑定实体。
 * 每个工作流步骤绑定到一个 AI 模型配置（ai_model_config 表）。
 */
@Data
@TableName("step_model_binding")
public class StepModelBinding implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;

    /** 步骤编码（SUMMARY/STORYBOARD/ASSET_DESIGN/ASSET_IMAGE/ASSET_DERIVE/STORYBOARD_IMAGE/AUDIO/VIDEO） */
    private String stepCode;

    /** 步骤中文名 */
    private String stepName;

    /** 步骤顺序（1-8） */
    private Integer stepOrder;

    /** 关联 ai_model_config 表的 ID（可为空） */
    private Long modelConfigId;

    /** 模型类型（1文本 2图像 3音频 4视频，由步骤定义，不可为空） */
    private Integer modelType;

    /** 关联的模型配置（非数据库字段，查询时填充） */
    @TableField(exist = false)
    private AiModelConfig modelConfig;
}
