package cn.edu.zjut.backend.controller;

import cn.edu.zjut.backend.common.Result;
import cn.edu.zjut.backend.dto.LoginRequest;
import cn.edu.zjut.backend.dto.LoginResponse;
import cn.edu.zjut.backend.dto.UserInfo;
import cn.edu.zjut.backend.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口：登录与当前用户。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(authService.login(request));
    }

    @GetMapping("/me")
    public Result<UserInfo> me() {
        return Result.ok(authService.me());
    }
}
