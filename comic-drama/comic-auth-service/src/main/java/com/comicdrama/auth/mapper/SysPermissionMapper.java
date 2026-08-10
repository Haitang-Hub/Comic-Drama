package com.comicdrama.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.comicdrama.auth.entity.SysPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysPermissionMapper extends BaseMapper<SysPermission> {

    /** 查询用户的权限编码列表（去重） */
    @Select("SELECT DISTINCT p.perm_code FROM sys_permission p " +
            "INNER JOIN sys_role_permission rp ON p.id = rp.permission_id " +
            "INNER JOIN sys_user_role ur ON rp.role_id = ur.role_id " +
            "WHERE ur.user_id = #{userId} AND p.deleted = 0 AND p.status = 1")
    List<String> selectPermCodes(@Param("userId") Long userId);
}
