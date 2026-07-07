package com.yueqian.ticketassistant.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yueqian.ticketassistant.dto.ApiResponse;
import com.yueqian.ticketassistant.dto.LoginRequest;
import com.yueqian.ticketassistant.dto.LoginResponse;
import com.yueqian.ticketassistant.dto.RegisterRequest;
import com.yueqian.ticketassistant.entity.AppUser;
import com.yueqian.ticketassistant.mapper.AppUserMapper;
import com.yueqian.ticketassistant.service.JwtService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final JwtService jwtService;
    private final AppUserMapper userMapper;

    public AuthController(JwtService jwtService, AppUserMapper userMapper) {
        this.jwtService = jwtService;
        this.userMapper = userMapper;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        AppUser user = userMapper.selectOne(new LambdaQueryWrapper<AppUser>().eq(AppUser::getUsername, request.username()));
        if (user == null || user.getEnabled() == null || user.getEnabled() != 1
                || !hash(request.password(), user.getPasswordSalt()).equalsIgnoreCase(user.getPasswordHash())) {
            throw new IllegalArgumentException("账号或密码不正确");
        }
        if (request.expectedRole() != null && !request.expectedRole().isBlank()
                && !request.expectedRole().equalsIgnoreCase(user.getRole())) {
            throw new IllegalArgumentException("账号类型不匹配");
        }
        return ApiResponse.ok(new LoginResponse(jwtService.issue(user.getUsername(), user.getRole()),
                user.getUsername(), user.getDisplayName(), user.getRole()));
    }

    @PostMapping("/register")
    public ApiResponse<Void> register(@Valid @RequestBody RegisterRequest request) {
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<AppUser>().eq(AppUser::getUsername, request.username()));
        if (exists != null && exists > 0) {
            throw new IllegalArgumentException("账号已存在");
        }
        String salt = UUID.randomUUID().toString().replace("-", "");
        AppUser user = new AppUser();
        user.setUsername(request.username().trim());
        user.setDisplayName(request.displayName().trim());
        user.setPasswordSalt(salt);
        user.setPasswordHash(hash(request.password(), salt));
        user.setRole("USER");
        user.setEnabled(1);
        user.setCreateTime(LocalDateTime.now());
        userMapper.insert(user);
        return ApiResponse.ok(null);
    }

    private String hash(String password, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((password + salt).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : bytes) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
