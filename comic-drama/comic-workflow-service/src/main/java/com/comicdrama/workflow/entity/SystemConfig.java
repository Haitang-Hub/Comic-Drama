package com.comicdrama.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.comicdrama.common.entity.BaseTimeEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统配置表（无逻辑删除字段）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("system_config")
public class SystemConfig extends BaseTimeEntity {
    private String configKey;
    private String configValue;
    private Integer valueType;
    private String configName;
    private String description;
    private Integer isSystem;
    private Integer status;
}