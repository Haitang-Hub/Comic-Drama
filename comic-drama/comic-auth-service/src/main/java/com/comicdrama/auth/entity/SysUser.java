package com.comicdrama.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.comicdrama.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 用户表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseEntity {

    private String username;

    private String password;

    private String nickname;

    private String avatar;

    private String email;

    private String phone;

    /** 性别：0未知 1男 2女 */
    private Integer gender;

    /** 账号状态：0禁用 1启用 */
    private Integer status;

    private LocalDateTime lastLoginTime;

    private String lastLoginIp;

    private String remark;
}
