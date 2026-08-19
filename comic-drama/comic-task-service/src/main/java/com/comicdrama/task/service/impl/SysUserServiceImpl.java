package com.comicdrama.task.service.impl;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.comicdrama.task.entity.SysUser;
import com.comicdrama.task.mapper.SysUserMapper;
import com.comicdrama.task.service.SysUserService;
import com.comicdrama.task.vo.SysUserVO;
import com.comicdrama.common.dto.PageQuery;
import com.comicdrama.common.result.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    @Override
    public PageResult<SysUserVO> page(PageQuery query, String keyword) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(SysUser::getUsername, keyword)
                    .or().like(SysUser::getNickname, keyword);
        }
        wrapper.orderByDesc(SysUser::getCreateTime);
        Page<SysUser> page = new Page<>(query.getPage(), query.getSize());
        Page<SysUser> result = this.page(page, wrapper);

        // 转换为 VO 并填充角色信息
        List<SysUserVO> voList = result.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        return new PageResult<>(voList, result.getTotal(), result.getCurrent(), result.getSize());
    }

    /**
     * 将 SysUser 转换为 SysUserVO，并填充角色名称列表
     */
    private SysUserVO convertToVO(SysUser user) {
        SysUserVO vo = new SysUserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setEmail(user.getEmail());
        vo.setPhone(user.getPhone());
        vo.setGender(user.getGender());
        vo.setStatus(user.getStatus());
        vo.setCreateTime(user.getCreateTime());
        vo.setUpdateTime(user.getUpdateTime());
        vo.setRemark(user.getRemark());
        // 密码脱敏
        vo.setPassword(null);

        vo.setRole(user.getRole());

        return vo;
    }

    @Override
    public void resetPassword(Long id, String newPwd) {
        SysUser update = new SysUser();
        update.setId(id);
        update.setPassword(BCrypt.hashpw(newPwd, BCrypt.gensalt()));
        this.updateById(update);
    }
}
