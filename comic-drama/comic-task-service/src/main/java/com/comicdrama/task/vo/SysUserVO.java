package com.comicdrama.task.vo;

import com.comicdrama.task.entity.SysUser;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户管理视图对象（继承 SysUser，密码脱敏）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysUserVO extends SysUser {
}
