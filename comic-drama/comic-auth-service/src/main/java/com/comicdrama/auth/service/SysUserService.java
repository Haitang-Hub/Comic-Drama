package com.comicdrama.auth.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.comicdrama.auth.entity.SysUser;
import com.comicdrama.auth.vo.SysUserVO;
import com.comicdrama.common.dto.PageQuery;
import com.comicdrama.common.result.PageResult;

public interface SysUserService extends IService<SysUser> {

    PageResult<SysUserVO> page(PageQuery query, String keyword);

    void resetPassword(Long id, String newPwd);

    void assignRoles(Long userId, java.util.List<Long> roleIds);
}
