package com.comicdrama.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.comicdrama.common.entity.BaseTimeEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统配置表（并发限制、排队阈值、清理策略等通用配置）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("system_config")
public class SystemConfig extends BaseTimeEntity {

    /** 配置键 */
    private String configKey;

    private String configValue;

    /** 值类型：1字符串 2数字 3布尔 4JSON */
    private Integer valueType;

    private String configName;

    private String description;

    /** 是否系统内置：0否 1是（内置不可删） */
    private Integer isSystem;

    /** 状态：0禁用 1启用 */
    private Integer status;
}
