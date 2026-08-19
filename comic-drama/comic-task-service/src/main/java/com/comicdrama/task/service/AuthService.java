package com.comicdrama.task.service;

import com.comicdrama.task.dto.UpdateProfileDTO;
import com.comicdrama.task.dto.LoginDTO;
import com.comicdrama.task.dto.RegisterDTO;
import com.comicdrama.task.vo.LoginInfoVO;
import com.comicdrama.task.vo.UserProfileVO;
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
