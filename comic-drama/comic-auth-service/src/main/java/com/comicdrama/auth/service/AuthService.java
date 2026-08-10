package com.comicdrama.auth.service;

import com.comicdrama.auth.dto.UpdateProfileDTO;
import com.comicdrama.auth.dto.LoginDTO;
import com.comicdrama.auth.dto.RegisterDTO;
import com.comicdrama.auth.vo.LoginInfoVO;
import com.comicdrama.auth.vo.UserProfileVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface AuthService {

    LoginInfoVO login(LoginDTO dto, String loginIp);

    void logout();

    LoginInfoVO getCurrentUserInfo();

    Long register(RegisterDTO dto);

    UserProfileVO getCurrentUserProfile();

    UserProfileVO updateProfile(UpdateProfileDTO dto);

    void updatePassword(String oldPassword, String newPassword);

    String uploadAvatar(MultipartFile file);

    Map<String, Object> getUserStats();
}