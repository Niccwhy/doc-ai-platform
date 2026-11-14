package cn.edu.zjut.backend.auth.controller;

import cn.edu.zjut.backend.auth.dto.LoginRequest;
import cn.edu.zjut.backend.auth.dto.LoginResponse;
import cn.edu.zjut.backend.auth.service.AuthService;
import cn.edu.zjut.backend.common.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }
}
