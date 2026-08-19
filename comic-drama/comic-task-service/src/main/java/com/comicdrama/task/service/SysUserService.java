package com.comicdrama.task.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.comicdrama.task.entity.SysUser;
import com.comicdrama.task.vo.SysUserVO;
import com.comicdrama.common.dto.PageQuery;
import com.comicdrama.common.result.PageResult;

public interface SysUserService extends IService<SysUser> {

    PageResult<SysUserVO> page(PageQuery query, String keyword);

    void resetPassword(Long id, String newPwd);
}
