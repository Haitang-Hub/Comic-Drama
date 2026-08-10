package com.comicdrama.auth.service.impl;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.comicdrama.auth.entity.SysUser;
import com.comicdrama.auth.entity.SysUserRole;
import com.comicdrama.auth.mapper.SysRoleMapper;
import com.comicdrama.auth.mapper.SysUserMapper;
import com.comicdrama.auth.mapper.SysUserRoleMapper;
import com.comicdrama.auth.service.SysUserService;
import com.comicdrama.auth.vo.SysUserVO;
import com.comicdrama.common.dto.PageQuery;
import com.comicdrama.common.result.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysRoleMapper sysRoleMapper;

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

        // 查询角色名称列表
        List<String> roleCodes = sysRoleMapper.selectRoleCodes(user.getId());
        vo.setRoleNames(roleCodes != null ? roleCodes : new ArrayList<>());

        return vo;
    }

    @Override
    public void resetPassword(Long id, String newPwd) {
        SysUser update = new SysUser();
        update.setId(id);
        update.setPassword(BCrypt.hashpw(newPwd, BCrypt.gensalt()));
        this.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(Long userId, List<Long> roleIds) {
        sysUserRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        if (CollectionUtils.isEmpty(roleIds)) {
            return;
        }
        for (Long roleId : roleIds) {
            SysUserRole ur = new SysUserRole();
            ur.setUserId(userId);
            ur.setRoleId(roleId);
            sysUserRoleMapper.insert(ur);
        }
    }
}
