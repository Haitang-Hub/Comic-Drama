package com.comicdrama.task.controller;

import cn.hutool.extra.servlet.JakartaServletUtil;
import com.comicdrama.task.dto.UpdateProfileDTO;
import com.comicdrama.task.dto.LoginDTO;
import com.comicdrama.task.dto.RegisterDTO;
import com.comicdrama.task.service.AuthService;
import com.comicdrama.task.vo.LoginInfoVO;
import com.comicdrama.task.vo.UserProfileVO;
import com.comicdrama.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public Result<LoginInfoVO> login(@RequestBody @Valid LoginDTO dto, HttpServletRequest request) {
        String ip = JakartaServletUtil.getClientIP(request);
        return Result.ok(authService.login(dto, ip));
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        authService.logout();
        return Result.ok();
    }

    @GetMapping("/user/info")
    public Result<LoginInfoVO> userInfo() {
        return Result.ok(authService.getCurrentUserInfo());
    }

    @PostMapping("/register")
    public Result<Long> register(@RequestBody @Valid RegisterDTO dto) {
        return Result.ok(authService.register(dto));
    }

    @GetMapping("/profile")
    public Result<UserProfileVO> getProfile() {
        return Result.ok(authService.getCurrentUserProfile());
    }

    @PutMapping("/profile")
    public Result<UserProfileVO> updateProfile(@RequestBody UpdateProfileDTO dto) {
        return Result.ok(authService.updateProfile(dto));
    }

    @PutMapping("/password")
    public Result<Void> updatePassword(
            @RequestParam String oldPassword,
            @RequestParam String newPassword) {
        authService.updatePassword(oldPassword, newPassword);
        return Result.ok();
    }

    @PostMapping("/avatar")
    public Result<String> uploadAvatar(@RequestParam("file") MultipartFile file) {
        return Result.ok(authService.uploadAvatar(file));
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> getStats() {
        return Result.ok(authService.getUserStats());
    }
}
