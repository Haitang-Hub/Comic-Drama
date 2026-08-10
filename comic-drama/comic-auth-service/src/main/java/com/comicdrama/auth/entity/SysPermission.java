package com.comicdrama.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.comicdrama.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 权限表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_permission")
public class SysPermission extends BaseEntity {

    private Long parentId;

    private String permCode;

    private String permName;

    /** 类型：1菜单 2按钮 3接口 */
    private Integer permType;

    private String path;

    private String component;

    private String icon;

    private Integer sort;

    /** 状态：0禁用 1启用 */
    private Integer status;
}
