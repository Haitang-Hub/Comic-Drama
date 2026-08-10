package com.comicdrama.auth.service.impl;

import cn.dev33.satoken.stp.SaLoginModel;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.comicdrama.auth.dto.UpdateProfileDTO;
import com.comicdrama.auth.dto.LoginDTO;
import com.comicdrama.auth.dto.RegisterDTO;
import com.comicdrama.auth.entity.SysRole;
import com.comicdrama.auth.entity.SysUser;
import com.comicdrama.auth.entity.SysUserRole;
import com.comicdrama.auth.mapper.SysPermissionMapper;
import com.comicdrama.auth.mapper.SysRoleMapper;
import com.comicdrama.auth.mapper.SysUserRoleMapper;
import com.comicdrama.auth.mapper.SysUserMapper;
import com.comicdrama.auth.service.AuthService;
import com.comicdrama.auth.vo.LoginInfoVO;
import com.comicdrama.auth.vo.UserInfoVO;
import com.comicdrama.auth.vo.UserProfileVO;
import com.comicdrama.common.constant.SecurityConstants;
import com.comicdrama.common.exception.BizException;
import com.comicdrama.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysPermissionMapper sysPermissionMapper;

    @Value("${upload.avatar-path:/data/avatars}")
    private String avatarPath;

    @Override
    public LoginInfoVO login(LoginDTO dto, String loginIp) {
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, dto.getUsername()));
        if (user == null) {
            throw new BizException(ResultCode.USER_NOT_FOUND);
        }
        if (user.getStatus() == null || user.getStatus() == 0) {
            throw new BizException(ResultCode.ACCOUNT_DISABLED);
        }
        if (!BCrypt.checkpw(dto.getPassword(), user.getPassword())) {
            throw new BizException(ResultCode.USERNAME_OR_PASSWORD_ERROR);
        }

        List<String> roles = sysRoleMapper.selectRoleCodes(user.getId());
        List<String> permissions = sysPermissionMapper.selectPermCodes(user.getId());

        StpUtil.login(user.getId(), new SaLoginModel()
                .setExtra("roles", roles)
                .setExtra("permissions", permissions)
                .setExtra("username", user.getUsername()));

        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setLastLoginTime(LocalDateTime.now());
        update.setLastLoginIp(loginIp);
        sysUserMapper.updateById(update);

        UserInfoVO userInfo = new UserInfoVO();
        BeanUtils.copyProperties(user, userInfo);

        return LoginInfoVO.builder()
                .tokenName(StpUtil.getTokenName())
                .tokenValue(StpUtil.getTokenValue())
                .userInfo(userInfo)
                .roles(roles)
                .permissions(permissions)
                .build();
    }

    @Override
    public void logout() {
        StpUtil.logout();
    }

    @Override
    public LoginInfoVO getCurrentUserInfo() {
        Long userId = StpUtil.getLoginIdAsLong();
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.USER_NOT_FOUND);
        }
        List<String> roles = sysRoleMapper.selectRoleCodes(userId);
        List<String> permissions = sysPermissionMapper.selectPermCodes(userId);
        UserInfoVO userInfo = new UserInfoVO();
        BeanUtils.copyProperties(user, userInfo);
        return LoginInfoVO.builder()
                .tokenName(StpUtil.getTokenName())
                .tokenValue(StpUtil.getTokenValue())
                .userInfo(userInfo)
                .roles(roles)
                .permissions(permissions)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long register(RegisterDTO dto) {
        Long exists = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, dto.getUsername()));
        if (exists != null && exists > 0) {
            throw new BizException(ResultCode.USERNAME_EXISTS);
        }
        SysUser user = new SysUser();
        user.setUsername(dto.getUsername());
        user.setPassword(BCrypt.hashpw(dto.getPassword(), BCrypt.gensalt()));
        user.setNickname(dto.getNickname() == null ? dto.getUsername() : dto.getNickname());
        user.setEmail(dto.getEmail());
        user.setPhone(dto.getPhone());
        user.setGender(0);
        user.setStatus(1);
        sysUserMapper.insert(user);

        SysRole userRole = sysRoleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, SecurityConstants.ROLE_USER));
        if (userRole != null) {
            SysUserRole ur = new SysUserRole();
            ur.setUserId(user.getId());
            ur.setRoleId(userRole.getId());
            sysUserRoleMapper.insert(ur);
        } else {
            log.warn("默认 USER 角色不存在，用户 {} 未绑定角色", user.getUsername());
        }
        return user.getId();
    }

    @Override
    public UserProfileVO getCurrentUserProfile() {
        Long userId = StpUtil.getLoginIdAsLong();
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.USER_NOT_FOUND);
        }
        return toProfileVO(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserProfileVO updateProfile(UpdateProfileDTO dto) {
        Long userId = StpUtil.getLoginIdAsLong();
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.USER_NOT_FOUND);
        }

        SysUser update = new SysUser();
        update.setId(userId);
        if (dto.getNickname() != null) {
            update.setNickname(dto.getNickname());
        }
        if (dto.getEmail() != null) {
            update.setEmail(dto.getEmail());
        }
        if (dto.getPhone() != null) {
            update.setPhone(dto.getPhone());
        }
        if (dto.getGender() != null) {
            update.setGender(dto.getGender());
        }
        sysUserMapper.updateById(update);

        SysUser refreshed = sysUserMapper.selectById(userId);
        return toProfileVO(refreshed);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePassword(String oldPassword, String newPassword) {
        Long userId = StpUtil.getLoginIdAsLong();
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.USER_NOT_FOUND);
        }
        if (!BCrypt.checkpw(oldPassword, user.getPassword())) {
            throw new BizException(ResultCode.USERNAME_OR_PASSWORD_ERROR);
        }
        SysUser update = new SysUser();
        update.setId(userId);
        update.setPassword(BCrypt.hashpw(newPassword, BCrypt.gensalt()));
        sysUserMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String uploadAvatar(MultipartFile file) {
        Long userId = StpUtil.getLoginIdAsLong();
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.USER_NOT_FOUND);
        }
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST);
        }

        File dir = new File(avatarPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        String originalFilename = file.getOriginalFilename();
        String ext = originalFilename != null && originalFilename.contains(".")
                ? originalFilename.substring(originalFilename.lastIndexOf("."))
                : ".png";
        String fileName = UUID.randomUUID().toString().replace("-", "") + ext;
        File dest = new File(dir, fileName);

        try {
            file.transferTo(dest);
        } catch (IOException e) {
            log.error("头像上传失败", e);
            throw new BizException(ResultCode.INTERNAL_ERROR);
        }

        String avatarUrl = "/avatars/" + fileName;
        SysUser update = new SysUser();
        update.setId(userId);
        update.setAvatar(avatarUrl);
        sysUserMapper.updateById(update);

        return avatarUrl;
    }

    @Override
    public Map<String, Object> getUserStats() {
        Long userId = StpUtil.getLoginIdAsLong();
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.USER_NOT_FOUND);
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("userId", userId);
        stats.put("username", user.getUsername());
        stats.put("nickname", user.getNickname());
        stats.put("avatar", user.getAvatar());
        stats.put("email", user.getEmail());
        stats.put("phone", user.getPhone());
        stats.put("status", user.getStatus());
        stats.put("lastLoginTime", user.getLastLoginTime());

        return stats;
    }

    private UserProfileVO toProfileVO(SysUser user) {
        UserProfileVO vo = new UserProfileVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setEmail(user.getEmail());
        vo.setPhone(user.getPhone());
        vo.setGender(user.getGender());
        vo.setStatus(user.getStatus());
        return vo;
    }
}