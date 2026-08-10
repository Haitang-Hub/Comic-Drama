package com.comicdrama.auth.vo;

import com.comicdrama.auth.entity.SysUser;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 用户管理视图对象（含角色名称列表）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysUserVO extends SysUser {

    /** 角色名称列表（如 ["ADMIN", "USER"]） */
    private List<String> roleNames;
}
